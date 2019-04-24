package uk.gov.hmrc.play.fsm

import play.api.data.Form
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

/**
  * Base controller for journeys based on Finite State Machine.
  *
  * Provides 2 extension points:
  *   - getCallFor: what is the endpoint representing given state
  *   - renderState: how to render given state
  *
  * and few action creation helpers:
  *   - action
  *   - authorised
  *   - authorisedWithForm
  *   - authorisedWithBootstrapAndForm
  *   - authorisedShowCurrentStateWhen
  *   - whenCurrentStateMatches
  *   - showCurrentStateWhen
  *   - whenAuthorisedAndCurrentStateMatches
  *   - showCurrentStateWhenAuthorised
  */
trait JourneyController extends HeaderCarrierProvider {

  /** This has to be injected in the concrete controller */
  val journeyService: JourneyService

  import journeyService.StateAndBreadcrumbs
  import journeyService.model.{State, Transition, TransitionNotAllowed}

  /** root call of this journey, used as fallback for back links*/
  val root: Call

  /** implement this to map states into endpoints for redirection and back linking */
  def getCallFor(state: State)(implicit request: Request[_]): Call

  /** implement this to render state after transition or when form validation fails */
  def renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(
    implicit request: Request[_]): Result

  type Route        = Request[_] => Result
  type RouteFactory = StateAndBreadcrumbs => Route

  /** displays template for the state and breadcrumbs */
  val display: RouteFactory = (state: StateAndBreadcrumbs) =>
    (request: Request[_]) => renderState(state._1, state._2, None)(request)

  /** redirects to the endpoint matching state */
  val redirect: RouteFactory =
    (state: StateAndBreadcrumbs) => (request: Request[_]) => Results.Redirect(getCallFor(state._1)(request))

  /** applies transition to the current state */
  def apply(
    transition: Transition,
    routeFactory: RouteFactory)(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Future[Result] =
    journeyService
      .apply(transition)
      .map(routeFactory)
      .map(_(request))
      .recover {
        case TransitionNotAllowed(origin, breadcrumbs, _) =>
          routeFactory(origin, breadcrumbs)(request) // renders current state back
      }

  protected def backLinkFor(breadcrumbs: List[State])(implicit request: Request[_]): Call =
    breadcrumbs.headOption.map(getCallFor).getOrElse(root)

  protected final def action(body: Request[_] => Future[Result]): Action[AnyContent] = Action.async {
    implicit request =>
      body(request)
  }

  type WithAuthorised[User] = Request[_] => (User => Future[Result]) => Future[Result]

  protected final def authorised[User](withAuthorised: WithAuthorised[User])(transition: User => Transition)(
    routeFactory: RouteFactory)(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      apply(transition(user), routeFactory)
    }

  protected final def authorisedWithForm[User, Payload](withAuthorised: WithAuthorised[User])(form: Form[Payload])(
    transition: User => Payload => Transition)(
    implicit hc: HeaderCarrier,
    request: Request[_],
    ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      bindForm(form, transition(user))
    }

  protected final def authorisedWithBootstrapAndForm[User, Payload, T](bootstrap: Transition)(
    withAuthorised: WithAuthorised[User])(form: Form[Payload])(transition: User => Payload => Transition)(
    implicit hc: HeaderCarrier,
    request: Request[_],
    ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      journeyService
        .apply(bootstrap)
        .flatMap(_ => bindForm(form, transition(user)))
    }

  private def bindForm[T](form: Form[T], transition: T => Transition)(
    implicit hc: HeaderCarrier,
    request: Request[_],
    ec: ExecutionContext): Future[Result] =
    form
      .bindFromRequest()
      .fold(
        formWithErrors =>
          journeyService.currentState.flatMap {
            case Some((state, _)) =>
              Future.successful(
                Results
                  .Redirect(getCallFor(state))
                  .flashing(Flash {
                    val data = formWithErrors.data
                    if (data.isEmpty) Map("dummy" -> "") else data
                  }))
            case None =>
              apply(journeyService.model.start, redirect)
        },
        userInput => apply(transition(userInput), redirect)
      )

  type ExpectedStates = PartialFunction[State, Unit]

  protected final def showCurrentStateWhen(expectedStates: ExpectedStates)(
    implicit ec: ExecutionContext): Action[AnyContent] =
    whenCurrentStateMatches(expectedStates)(redirect)(ec)

  protected final def whenCurrentStateMatches(expectedStates: ExpectedStates)(routeFactory: RouteFactory)(
    implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val headerCarrier: HeaderCarrier = hc(request)
      for {
        stateAndBreadcrumbsOpt <- journeyService.currentState
        result <- stateAndBreadcrumbsOpt match {
                   case None => apply(journeyService.model.start, routeFactory)
                   case Some(stateAndBreadcrumbs) =>
                     if (hasMatchingState(expectedStates, stateAndBreadcrumbs))
                       journeyService.currentState
                         .flatMap(stepBackUntil(expectedStates))
                     else apply(journeyService.model.start, routeFactory)
                 }
      } yield result
    }

  protected final def showCurrentStateWhenAuthorised[User](withAuthorised: WithAuthorised[User])(
    expectedStates: ExpectedStates)(implicit ec: ExecutionContext): Action[AnyContent] =
    whenAuthorisedAndCurrentStateMatches(withAuthorised)(expectedStates)(redirect)(ec)

  protected final def whenAuthorisedAndCurrentStateMatches[User](withAuthorised: WithAuthorised[User])(
    expectedStates: ExpectedStates)(routeFactory: RouteFactory)(implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val headerCarrier: HeaderCarrier = hc(request)
      withAuthorised(request) { _ =>
        for {
          stateAndBreadcrumbsOpt <- journeyService.currentState
          result <- stateAndBreadcrumbsOpt match {
                     case None => apply(journeyService.model.start, routeFactory)
                     case Some(stateAndBreadcrumbs) =>
                       if (hasMatchingState(expectedStates, stateAndBreadcrumbs))
                         journeyService.currentState
                           .flatMap(stepBackUntil(expectedStates))
                       else apply(journeyService.model.start, routeFactory)
                   }
        } yield result
      }
    }

  @tailrec
  private def hasMatchingState(
    filter: PartialFunction[State, Unit],
    stateAndBreadcrumbs: StateAndBreadcrumbs): Boolean =
    stateAndBreadcrumbs match {
      case (state, breadcrumbs) =>
        if (filter.isDefinedAt(state)) true
        else
          breadcrumbs match {
            case Nil    => false
            case s :: b => hasMatchingState(filter, (s, b))
          }
    }

  private def stepBackUntil(filter: PartialFunction[State, Unit])(stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs])(
    implicit hc: HeaderCarrier,
    request: Request[_],
    ec: ExecutionContext): Future[Result] = stateAndBreadcrumbsOpt match {
    case None => apply(journeyService.model.start, redirect)
    case Some((state, breadcrumbs)) =>
      if (filter.isDefinedAt(state))
        Future.successful(renderState(state, breadcrumbs, None)(request))
      else journeyService.stepBack.flatMap(stepBackUntil(filter))
  }
}

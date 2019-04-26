package uk.gov.hmrc.play.fsm

import play.api.data.Form
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

/**
  * Base controller for journeys based on Finite State Machine.
  *
  * Provides 2 main extension points:
  *   - getCallFor: what is the endpoint representing given state
  *   - renderState: how to render given state
  *
  * and action creation helpers:
  *   - action
  *   - actionShowState
  *   - actionShowStateWhenAuthorised
  *
  * and async result functions:
  *   - showState
  *   - showStateWhenAuthorised
  *   - whenAuthorised
  *   - whenAuthorisedWithForm
  *   - whenAuthorisedWithBootstrapAndForm
  *   - bindForm
  */
trait JourneyController extends HeaderCarrierProvider {

  /** This has to be injected in the concrete controller */
  val journeyService: JourneyService

  import journeyService.{Breadcrumbs, StateAndBreadcrumbs}
  import journeyService.model.{State, Transition, TransitionNotAllowed}

  /** implement to map states into endpoints for redirection and back linking */
  def getCallFor(state: State)(implicit request: Request[_]): Call

  /** implement to render state after transition or when form validation fails */
  def renderState(state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]])(
    implicit request: Request[_]): Result

  /** interceptor: override to do basic checks on every incoming request (headers, session, etc.) */
  def withValidRequest(
    body: => Future[Result])(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Future[Result] =
    body

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

  /** gets call to the previous state */
  protected def backLinkFor(breadcrumbs: Breadcrumbs)(implicit request: Request[_]): Call =
    breadcrumbs.headOption.map(getCallFor).getOrElse(getCallFor(journeyService.model.root))

  /** action for a given async result function */
  protected final def action(body: Request[_] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val headerCarrier: HeaderCarrier = hc(request)
      withValidRequest(body(request))
    }

  type WithAuthorised[User] = Request[_] => (User => Future[Result]) => Future[Result]

  /** applies transition parametrized by an authorised user */
  protected final def whenAuthorised[User](withAuthorised: WithAuthorised[User])(transition: User => Transition)(
    routeFactory: RouteFactory)(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      apply(transition(user), routeFactory)
    }

  /** applies transition parametrized by an authorised user and form output */
  protected final def whenAuthorisedWithForm[User, Payload](withAuthorised: WithAuthorised[User])(form: Form[Payload])(
    transition: User => Payload => Transition)(
    implicit hc: HeaderCarrier,
    request: Request[_],
    ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      bindForm(form, transition(user))
    }

  /** applies 2 transitions in order:
    * - first transition as provided
    * - second transition parametrized by an authorised user and form output */
  protected final def whenAuthorisedWithBootstrapAndForm[User, Payload, T](bootstrap: Transition)(
    withAuthorised: WithAuthorised[User])(form: Form[Payload])(transition: User => Payload => Transition)(
    implicit hc: HeaderCarrier,
    request: Request[_],
    ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      journeyService
        .apply(bootstrap)
        .flatMap(_ => bindForm(form, transition(user)))
    }

  /** binds form and applies transition parametrized by its outcome if success,
    * otherwise redirects to the current state with form errors in flash scope */
  protected final def bindForm[T](form: Form[T], transition: T => Transition)(
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
                    // dummy parameter required if empty data
                    if (data.isEmpty) Map("dummy" -> "") else data
                  }))
            case None =>
              apply(journeyService.model.start, redirect)
        },
        userInput => apply(transition(userInput), redirect)
      )

  type ExpectedStates = PartialFunction[State, Unit]

  /** action rendering current state or rewinding to the previous state matching expectation */
  protected final def actionShowState(expectedStates: ExpectedStates)(
    implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val headerCarrier: HeaderCarrier = hc(request)
      showState(expectedStates)
    }

  /** renders current state or rewinds to the previous state matching expectation */
  protected final def showState(expectedStates: ExpectedStates)(
    implicit hc: HeaderCarrier,
    request: Request[_],
    ec: ExecutionContext): Future[Result] =
    for {
      stateAndBreadcrumbsOpt <- journeyService.currentState
      result <- stateAndBreadcrumbsOpt match {
                 case None => apply(journeyService.model.start, redirect)
                 case Some(stateAndBreadcrumbs) =>
                   if (hasState(expectedStates, stateAndBreadcrumbs))
                     journeyService.currentState
                       .flatMap(rewindTo(expectedStates))
                   else apply(journeyService.model.start, redirect)
               }
    } yield result

  /** action rendering current state or rewinding to the previous state matching expectation */
  protected final def actionShowStateWhenAuthorised[User](withAuthorised: WithAuthorised[User])(
    expectedStates: ExpectedStates)(implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val headerCarrier: HeaderCarrier = hc(request)
      showStateWhenAuthorised(withAuthorised)(expectedStates)
    }

  /** when user authorized then render current state or rewinds to the previous state matching expectation */
  protected final def showStateWhenAuthorised[User](withAuthorised: WithAuthorised[User])(
    expectedStates: ExpectedStates)(
    implicit hc: HeaderCarrier,
    request: Request[_],
    ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { _ =>
      showState(expectedStates)
    }

  /** checks if some expected state can be found in journey history (breadcrumbs) */
  @tailrec
  protected final def hasState(
    filter: PartialFunction[State, Unit],
    stateAndBreadcrumbs: StateAndBreadcrumbs): Boolean =
    stateAndBreadcrumbs match {
      case (state, breadcrumbs) =>
        if (filter.isDefinedAt(state)) true
        else
          breadcrumbs match {
            case Nil    => false
            case s :: b => hasState(filter, (s, b))
          }
    }

  /** rewinds journey state and history (breadcrumbs) back to the latest expected state */
  protected final def rewindTo(expectedState: PartialFunction[State, Unit])(
    stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs])(
    implicit hc: HeaderCarrier,
    request: Request[_],
    ec: ExecutionContext): Future[Result] = stateAndBreadcrumbsOpt match {
    case None => apply(journeyService.model.start, redirect)
    case Some((state, breadcrumbs)) =>
      if (expectedState.isDefinedAt(state))
        Future.successful(renderState(state, breadcrumbs, None)(request))
      else journeyService.stepBack.flatMap(rewindTo(expectedState))
  }
}

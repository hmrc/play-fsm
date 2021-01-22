/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.fsm

import play.api.data.Form
import play.api.mvc._

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import play.api.libs.json.Reads
import java.util.concurrent.TimeoutException

/**
  * Controller mixin for journeys implementing Finite State Machine.
  *
  * Provides 3 main extension points:
  *   - getCallFor: what is the endpoint representing given state
  *   - renderState: how to render given state
  *   - context: custom request context to carry over to the service layer
  *
  * and actions DSL
  */
trait JourneyController[RequestContext] {

  /** This has to be injected in the concrete controller */
  val journeyService: JourneyService[RequestContext]

  import journeyService.{Breadcrumbs, StateAndBreadcrumbs}
  import journeyService.model.{Merger, State, StayInCurrentState, Transition, TransitionNotAllowed}

  //-------------------------------------------------
  // EXTENSION POINTS
  //-------------------------------------------------

  /** Implement to map states into endpoints for redirection and back linking */
  def getCallFor(state: State)(implicit request: Request[_]): Call

  /** Implement to render state after transition or when form validation fails */
  def renderState(state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result

  /** Implement to provide customized request context you require in the service layer * */
  def context(implicit rh: RequestHeader): RequestContext

  /** Interceptor: override to do basic checks on every incoming request (headers, session, etc.) */
  def withValidRequest(
    body: => Future[Result]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    body

  //-------------------------------------------------
  // INTERNAL TYPE ALIASES
  //-------------------------------------------------

  type Outcome        = Request[_] => Result
  type OutcomeFactory = StateAndBreadcrumbs => Outcome
  type Renderer       = Request[_] => (State, Breadcrumbs, Option[Form[_]]) => Result
  type Fallback       = (RequestContext, Request[_], ExecutionContext) => Future[Result]

  protected final def is[S <: State: ClassTag](state: State): Boolean =
    implicitly[ClassTag[S]].runtimeClass.isAssignableFrom(state.getClass)

  //-------------------------------------------------
  // AVAILABLE OUTCOMES
  //-------------------------------------------------

  /** Display the current state using [[renderState]]. */
  protected final val display: OutcomeFactory = (state: StateAndBreadcrumbs) =>
    (request: Request[_]) => renderState(state._1, state._2, None)(request)

  /** Display the current state using custom renderer. */
  protected final def displayUsing(renderState: Renderer): OutcomeFactory =
    (state: StateAndBreadcrumbs) =>
      (request: Request[_]) => renderState(request)(state._1, state._2, None)

  /** Redirect to the current state using URL returned by [[getCallFor]]. */
  protected final val redirect: OutcomeFactory =
    (state: StateAndBreadcrumbs) =>
      (request: Request[_]) => Results.Redirect(getCallFor(state._1)(request))

  /**
    * If the current state is of type S then display using [[renderState]],
    * otherwise redirect using URL returned by [[getCallFor]].
    */
  protected final def redirectOrDisplayIf[S <: State: ClassTag]: OutcomeFactory = {
    case sb @ (state, breadcrumbs) =>
      if (is[S](state)) display(sb) else redirect(sb)
  }

  /**
    * If the current state is of type S then display using custom renderer,
    * otherwise redirect using URL returned by [[getCallFor]].
    */
  protected final def redirectOrDisplayUsingIf[S <: State: ClassTag](
    renderState: Renderer
  ): OutcomeFactory = {
    case sb @ (state, breadcrumbs) =>
      if (is[S](state)) displayUsing(renderState)(sb) else redirect(sb)
  }

  /**
    * If the current state is of type S then redirect using URL returned by [[getCallFor]],
    * otherwise display using [[renderState]].
    */
  protected final def displayOrRedirectIf[S <: State: ClassTag]: OutcomeFactory = {
    case sb @ (state, breadcrumbs) =>
      if (is[S](state)) redirect(sb) else display(sb)
  }

  /**
    * If the current state is of type S then redirect using URL returned by [[getCallFor]],
    * otherwise display using custom renderer.
    */
  protected final def displayUsingOrRedirectIf[S <: State: ClassTag](
    renderState: Renderer
  ): OutcomeFactory = {
    case sb @ (state, breadcrumbs) =>
      if (is[S](state)) redirect(sb) else displayUsing(renderState)(sb)
  }

  /**
    * If the current state is same as the origin then display using [[renderState]],
    * otherwise redirect using URL returned by [[getCallFor]].
    */
  protected final def redirectOrDisplayIfSame(origin: State): OutcomeFactory = {
    case sb @ (state, breadcrumbs) =>
      if (state == origin) display(sb) else redirect(sb)
  }

  /**
    * If the current state is same as the origin then display using custom renderer,
    * otherwise redirect using URL returned by [[getCallFor]].
    */
  protected final def redirectOrDisplayUsingIfSame(
    origin: State,
    renderState: Renderer
  ): OutcomeFactory = {
    case sb @ (state, breadcrumbs) =>
      if (state == origin) displayUsing(renderState)(sb) else redirect(sb)
  }

  //-------------------------------------------------
  // BACKLINK HELPERS
  //-------------------------------------------------

  /** Returns a call to the previous state. */
  protected final def backLinkFor(breadcrumbs: Breadcrumbs)(implicit request: Request[_]): Call =
    breadcrumbs.headOption.map(getCallFor).getOrElse(getCallFor(journeyService.model.root))

  /**
    * Returns a call to the latest state of S type if exists,
    * otherwise returns a fallback call if defined,
    * or a call to the root state.
    */
  protected final def backLinkToMostRecent[S <: State: ClassTag](
    breadcrumbs: Breadcrumbs,
    fallback: Option[Call] = None
  )(implicit request: Request[_]): Call =
    breadcrumbs
      .find {
        case s: S => true
        case _    => false
      }
      .map(getCallFor)
      .getOrElse(fallback.getOrElse(getCallFor(journeyService.model.root)))

  //-------------------------------------------------
  // TRANSITION HELPERS
  //-------------------------------------------------

  /**
    * Apply state transition and redirect to the URL matching the new state.
    */
  protected final def apply(transition: Transition, outcomeFactory: OutcomeFactory)(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    journeyService
      .apply(transition)
      .map(outcomeFactory)
      .map(_(request))
      .recoverWith {
        case TransitionNotAllowed(origin, breadcrumbs, _) =>
          Future.successful(
            outcomeFactory((origin, breadcrumbs))(request)
          )
        case StayInCurrentState =>
          journeyService.currentState.map {
            case Some((state, breadcrumbs)) =>
              outcomeFactory((state, breadcrumbs))(request)
            case None =>
              outcomeFactory((journeyService.model.root, Nil))(request)
          }
      }

  /** Default fallback result is to redirect back to the Start state. */
  protected final def redirectToStart(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    apply(journeyService.model.start, redirect)
      .flatMap(result =>
        journeyService
          .cleanBreadcrumbs(_ => Nil)
          .map(_ => result)
      )

  final val redirectToStartFallback: Fallback = redirectToStart(_, _, _)

  /** Sync view, redirect back to the current state. */
  protected final def redirectToCurrentState(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    journeyService.currentState
      .flatMap {
        case Some(sb) =>
          Future.successful(redirect(sb)(request))

        case None =>
          apply(journeyService.model.start, redirect)
      }

  final val redirectToCurrentStateFallback: Fallback = redirectToCurrentState(_, _, _)

  /** Override to change the default behaviour of the [[actions.show[State]]]. */
  val defaultShowFallback: Fallback = redirectToStartFallback

  //-------------------------------------------------
  // STATE RENDERING HELPERS
  //-------------------------------------------------

  type ExpectedStates = PartialFunction[State, Unit]

  /**
    * Display the state requested by the type parameter S.
    * If the current state is not of type S,
    * try rollback to the most recent state matching S,
    * or redirect back to the root state.
    * @tparam S type of the state to display
    */
  protected final def showState(
    expectedStates: ExpectedStates
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      sbopt <- journeyService.currentState
      result <- sbopt match {
                  case None => redirectToStart
                  case Some(stateAndBreadcrumbs) =>
                    if (hasState(expectedStates, stateAndBreadcrumbs))
                      journeyService.currentState
                        .flatMap(rollbackTo(expectedStates)(redirectToStart))
                    else redirectToStart
                }
    } yield result

  /**
    * Display the state requested by the type parameter S.
    * If the current state is not of type S,
    * when rollback flag is true try to rollback
    * to the most recent state matching S,
    * otherwise redirect back to the root state.
    *
    * @tparam S type of the state to display
    * @param outcomeFactory outcome factory to use
    * @param whether to rollback to the most recent state of type S
    */
  protected final def showState[S <: State: ClassTag](
    outcomeFactory: OutcomeFactory,
    rollback: Boolean = true,
    fallback: Fallback
  )(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    for {
      sbopt <- journeyService.currentState
      result <- sbopt match {
                  case Some(sb @ (state, breadcrumbs)) if is[S](state) =>
                    Future.successful(outcomeFactory(sb)(request))

                  case Some((state, breadcrumbs)) if `rollback` && hasRecentState[S](breadcrumbs) =>
                    rollbackTo[S](fallback, outcomeFactory)(sbopt)

                  case _ =>
                    fallback(rc, request, ec)
                }
    } yield result

  /**
    * Display the state requested by the type parameter S.
    * If the current state is not of type S,
    * when rollback flag is true try to rollback
    * to the most recent state matching S,
    * otherwise apply the transition and, depending on the outcome,
    * redirect to the new state or display if S.
    * If transition is not allowed then redirect back to the current state.
    *
    * @tparam S type of the state to display
    * @param transition transition to apply when state S not found
    * @param outcomeFactory outcome factory to use
    * @param whether to rollback to the most recent state of type S
    */
  protected final def showStateOrApply[S <: State: ClassTag](
    transition: Transition,
    outcomeFactory: OutcomeFactory,
    rollback: Boolean
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      sbopt <- journeyService.currentState
      result <- sbopt match {
                  case Some(sb @ (state, breadcrumbs)) if is[S](state) =>
                    Future.successful(outcomeFactory(sb)(request))

                  case Some((state, breadcrumbs)) if `rollback` && hasRecentState[S](breadcrumbs) =>
                    rollbackTo[S](apply(transition, outcomeFactory)(_, _, _), outcomeFactory)(sbopt)

                  case _ =>
                    apply(transition, outcomeFactory)
                }
    } yield result

  /**
    * Display the state requested by the type parameter S.
    * If the current state is not of type S
    * try rollback to the most recent state matching S
    * and apply merge function to reconcile the new state with the previous state,
    * or redirect back to the root state.
    */
  protected final def showStateWithRollbackUsingMerger[S <: State: ClassTag](
    merger: Merger[S],
    outcomeFactory: OutcomeFactory,
    fallback: Fallback
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      sbopt <- journeyService.currentState
      result <- sbopt match {
                  case Some((state, breadcrumbs))
                      if is[S](state) || hasRecentState[S](breadcrumbs) =>
                    rollbackAndModify[S](merger.withState(state))(fallback, outcomeFactory)(
                      sbopt
                    )
                  case _ =>
                    fallback(rc, request, ec)
                }
    } yield result

  /**
    * Display the journey state requested by the type parameter S.
    * If the current state is not of type S,
    * try rollback to the most recent state matching S
    * and apply merge function to reconcile the new state with the current state.
    * If there exists no matching state S in the history,
    * apply the transition and, depending on the outcome,
    * redirect to the new state or display if S.
    * If transition is not allowed then redirect back to the current state.
    * @tparam S type of the state to display
    */
  protected final def showStateUsingMergerOrApply[S <: State: ClassTag](
    merger: Merger[S],
    outcomeFactory: OutcomeFactory
  )(
    transition: Transition
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      sb <- journeyService.currentState
      result <- sb match {
                  case Some((state, breadcrumbs))
                      if is[S](state) || hasRecentState[S](breadcrumbs) =>
                    rollbackAndModify[S](merger.withState(state))(
                      apply(transition, outcomeFactory)(_, _, _),
                      outcomeFactory
                    )(sb)
                  case _ => apply(transition, outcomeFactory)
                }
    } yield result

  /**
    * Wait for a state to become of type S at most until maxTimestamp.
    * Check in intervals. If the timeout expires, execute ifTimeout.
    */
  protected final def waitFor[S <: State: ClassTag](
    interval: Long,
    maxTimestamp: Long
  )(outcomeFactory: OutcomeFactory)(ifTimeout: Request[_] => Future[Result])(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    journeyService.currentState.flatMap {
      case Some(sb @ (state, _)) if is[S](state) =>
        Future.successful(outcomeFactory(sb)(request))
      case _ =>
        if (System.nanoTime() > maxTimestamp) ifTimeout(request)
        else
          Schedule(interval) {
            waitFor[S](interval, maxTimestamp)(outcomeFactory)(ifTimeout)
          }
    }

  /** Check if the expected state exists in the journey history (breadcrumbs). */
  @tailrec
  protected final def hasState(
    filter: PartialFunction[State, Unit],
    stateAndBreadcrumbs: StateAndBreadcrumbs
  ): Boolean =
    stateAndBreadcrumbs match {
      case (state, breadcrumbs) =>
        if (filter.isDefinedAt(state)) true
        else
          breadcrumbs match {
            case Nil    => false
            case s :: b => hasState(filter, (s, b))
          }
    }

  /** Check if state of type S exists in the journey history (breadcrumbs). */
  protected final def hasRecentState[S <: State: ClassTag](breadcrumbs: Breadcrumbs): Boolean =
    breadcrumbs.exists(is[S])

  /** Rollback journey state and history (breadcrumbs) back to the most recent state matching expectation. */
  protected final def rollbackTo(expectedState: PartialFunction[State, Unit])(
    fallback: => Future[Result]
  )(
    stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    stateAndBreadcrumbsOpt match {
      case None => fallback
      case Some((state, breadcrumbs)) =>
        if (expectedState.isDefinedAt(state))
          Future.successful(renderState(state, breadcrumbs, None)(request))
        else journeyService.stepBack.flatMap(rollbackTo(expectedState)(fallback))
    }

  /** Rollback journey state and history (breadcrumbs) back to the most recent state of type S. */
  protected final def rollbackTo[S <: State: ClassTag](
    fallback: Fallback,
    outcomeFactory: OutcomeFactory
  )(
    stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    stateAndBreadcrumbsOpt match {
      case None => fallback(rc, request, ec)
      case Some(sb @ (state, _)) =>
        if (is[S](state))
          Future.successful(outcomeFactory(sb)(request))
        else
          journeyService.stepBack.flatMap(rollbackTo[S](fallback, outcomeFactory))
    }

  /**
    * Rollback journey state and history (breadcrumbs) back to the most recent state of type S,
    * and if exists, apply modification.
    */
  private final def rollbackAndModify[S <: State: ClassTag](modification: S => S)(
    fallback: Fallback,
    outcomeFactory: OutcomeFactory
  )(
    stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    stateAndBreadcrumbsOpt match {
      case None => fallback(rc, request, ec)
      case Some((state, _)) =>
        if (is[S](state))
          journeyService
            .modify(modification)
            .map(outcomeFactory)
            .map(_(request))
        else
          journeyService.stepBack.flatMap(rollbackAndModify(modification)(fallback, outcomeFactory))
    }

  //-------------------------------------------------
  // BINDING HELPERS
  //-------------------------------------------------

  /**
    * Bind form and apply transition if success,
    * otherwise redirect to the current state with form errors in the flash scope.
    */
  protected final def bindForm[T](
    form: Form[T],
    transition: T => Transition
  )(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    bindForm(form, transition, redirect, redirectToStart)

  /**
    * Bind form and apply transition if success,
    * otherwise redirect to the current state with form errors in the flash scope.
    */
  protected final def bindForm[T](
    form: Form[T],
    transition: T => Transition,
    outcomeFactory: OutcomeFactory,
    fallback: => Future[Result]
  )(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
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
                  })
              )
            case None =>
              fallback
          },
        userInput => apply(transition(userInput), outcomeFactory)
      )

  /**
    * Bind form derived from the current state and apply transition if success,
    * otherwise redirect to the current state with form errors in the flash scope.
    */
  protected final def bindFormDerivedFromState[T](
    form: State => Form[T],
    transition: T => Transition,
    outcomeFactory: OutcomeFactory,
    fallback: => Future[Result]
  )(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    journeyService.currentState.flatMap {
      case Some((state, _)) =>
        form(state)
          .bindFromRequest()
          .fold(
            formWithErrors =>
              Future.successful(
                Results
                  .Redirect(getCallFor(state))
                  .flashing(Flash {
                    val data = formWithErrors.data
                    // dummy parameter required if empty data
                    if (data.isEmpty) Map("dummy" -> "") else data
                  })
              ),
            userInput => apply(transition(userInput), outcomeFactory)
          )
      case None =>
        fallback
    }

  /**
    * Parse request's body as JSON and apply transition if success,
    * otherwise return ifFailure result.
    */
  protected final def parseJson[T: Reads](
    transition: T => Transition,
    outcomeFactory: OutcomeFactory
  )(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] = {
    val body = request.asInstanceOf[Request[AnyContent]].body
    body.asJson.flatMap(_.asOpt[T]) match {
      case Some(entity) =>
        apply(transition(entity), outcomeFactory)
      case None =>
        Future.failed(new IllegalArgumentException)
    }
  }

  //-------------------------------------------------
  // USER AUTHORIZATION HELPERS
  //-------------------------------------------------

  type WithAuthorised[User] = Request[_] => (User => Future[Result]) => Future[Result]

  /** Apply transition parametrized by an authorised user data. */
  protected final def whenAuthorised[User](
    withAuthorised: WithAuthorised[User]
  )(transition: User => Transition)(
    outcomeFactory: OutcomeFactory
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      apply(transition(user), outcomeFactory)
    }

  /** Apply transition parametrized by an authorised user data and form output. */
  protected final def whenAuthorisedWithForm[User, Payload](
    withAuthorised: WithAuthorised[User]
  )(form: Form[Payload])(
    transition: User => Payload => Transition
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      bindForm(form, transition(user))
    }

  /** Apply two transitions in order:
    * - first: simple transition
    * - second: transition parametrized by an authorised user data and form output
    */
  protected final def whenAuthorisedWithBootstrapAndForm[User, Payload, T](
    bootstrap: Transition
  )(withAuthorised: WithAuthorised[User])(form: Form[Payload])(
    transition: User => Payload => Transition
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      journeyService
        .apply(bootstrap)
        .flatMap(_ => bindForm(form, transition(user)))
    }

  /** When user is authorized then render current state or rewind to the previous state matching expectation. */
  protected final def showStateWhenAuthorised[User](withAuthorised: WithAuthorised[User])(
    expectedStates: ExpectedStates
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { _ =>
      showState(expectedStates)
    }

  // ---------------------------------------
  //  ACTIONS DSL
  // ---------------------------------------

  /** Implicitly converts Executable to an action. */
  implicit protected def build(executable: Executable)(implicit
    ec: ExecutionContext
  ): Action[AnyContent] =
    action(implicit request => executable.execute(Settings.default))

  /** Creates an action for a given async result function. */
  protected final def action(
    body: Request[_] => Future[Result]
  )(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val rc: RequestContext = context(request)
      withValidRequest(body(request))
    }

  /** Customizable execution-time settings. */
  case class Settings(
    outcomeFactoryOpt: Option[OutcomeFactory] = None
  ) {

    def setOutcomeFactory(outcomeFactory: OutcomeFactory): Settings =
      copy(outcomeFactoryOpt = Some(outcomeFactory))

    def setOutcomeFactory(outcomeFactoryOpt2: Option[OutcomeFactory]): Settings =
      copy(outcomeFactoryOpt = outcomeFactoryOpt2)

    def outcomeFactoryWithDefault(outcomeFactory: OutcomeFactory): OutcomeFactory =
      outcomeFactoryOpt.getOrElse(outcomeFactory)
  }

  object Settings {
    lazy val default = Settings()
  }

  /**
    * Interface of the action building blocks.
    *
    * Provides common post-processing functions:
    *
    * - [[display]] to force display
    * - [[displayUsing]] to force display using custom renderer
    * - [[redirect]] to force redirect
    * - [[redirectOrDisplayIfSame]] to display if state didn't change, otherwise redirect
    * - [[redirectOrDisplayUsingIfSame]] to display using custom renderer if state didn't change, otherwise redirect
    * - [[redirectOrDisplayIf]] to display if state of some type, otherwise redirect
    * - [[redirectOrDisplayUsingIf]] to display using custom renderer if state of some type, otherwise redirect
    * - [[displayOrRedirectIf]] to redirect if state of some type, otherwise display
    * - [[displayUsingOrRedirectIf]] to redirect if state of some type, otherwise display using custom renderer
    * - [[andCleanBreadcrumbs]] to clean history (breadcrumbs)
    * - [[transform]] to transform final result
    * - [[recover]] to recover from an error using custom strategy
    * - [[recoverWith]] to recover from an error using custom strategy
    */
  sealed trait Executable {

    /**
      * Execute and return the future of result.
      * @param outcomeFactoryOpt optional override of the outcomeFactory
      */
    def execute(
      settings: Settings
    )(implicit request: Request[_], ec: ExecutionContext): Future[Result]

    /**
      * Change how the result is created.
      * Force displaying the state after action using [[renderState]].
      */
    final def display: Executable = {
      val outer = this
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setOutcomeFactory(JourneyController.this.display))
      }
    }

    /**
      * Change how the result is created.
      * Force displaying the state after action using custom renderer.
      */
    final def displayUsing(renderState: Renderer): Executable = {
      val outer = this
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(
            settings.setOutcomeFactory(JourneyController.this.displayUsing(renderState))
          )
      }
    }

    /**
      * Change how the result is created.
      * Force redirect to the state after action using URL returned by [[getCallFor]].
      */
    final def redirect: Executable = {
      val outer = this
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setOutcomeFactory(JourneyController.this.redirect))
      }
    }

    /**
      * Change how the result is created.
      * If the state after action didn't change then display using [[renderState]],
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayIfSame: Executable = {
      val outer = this
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = context(request)
          journeyService.currentState.flatMap { current =>
            outer.execute(
              settings.setOutcomeFactory(current.map {
                case sb @ (state, breadcrumbs) =>
                  JourneyController.this.redirectOrDisplayIfSame(state)
              })
            )
          }
        }
      }
    }

    /**
      * Change how the result is created.
      * If the state after action didn't change then display using custom renderer,
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayUsingIfSame(renderState: Renderer): Executable = {
      val outer = this
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = context(request)
          journeyService.currentState.flatMap { current =>
            outer.execute(
              settings.setOutcomeFactory(current.map {
                case sb @ (state, breadcrumbs) =>
                  JourneyController.this.redirectOrDisplayUsingIfSame(state, renderState)
              })
            )
          }
        }
      }
    }

    /**
      * Change how the result is created.
      * If the state after action is of type S then display using [[renderState]],
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayIf[S <: State: ClassTag]: Executable = {
      val outer                          = this
      val outcomeFactory: OutcomeFactory = JourneyController.this.redirectOrDisplayIf[S]
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setOutcomeFactory(outcomeFactory))
      }
    }

    /**
      * Change how the result is created.
      * If the state after action is of type S then display using custom renderer,
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayUsingIf[S <: State: ClassTag](renderState: Renderer): Executable = {
      val outer = this
      val outcomeFactory: OutcomeFactory =
        JourneyController.this.redirectOrDisplayUsingIf[S](renderState)
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setOutcomeFactory(outcomeFactory))
      }
    }

    /**
      * Change how the result is created.
      * If the state after action is of type S then redirect using URL returned by [[getCallFor]]
      * otherwise display using [[renderState]],.
      */
    final def displayOrRedirectIf[S <: State: ClassTag]: Executable = {
      val outer                          = this
      val outcomeFactory: OutcomeFactory = JourneyController.this.displayOrRedirectIf[S]
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setOutcomeFactory(outcomeFactory))
      }
    }

    /**
      * Change how the result is created.
      * If the state after action is of type S then redirect using URL returned by [[getCallFor]],
      * otherwise display using custom renderer.
      */
    final def displayUsingOrRedirectIf[S <: State: ClassTag](renderState: Renderer): Executable = {
      val outer = this
      val outcomeFactory: OutcomeFactory =
        JourneyController.this.displayUsingOrRedirectIf[S](renderState)
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setOutcomeFactory(outcomeFactory))
      }
    }

    /** Clean history (breadcrumbs) after an action. */
    final def andCleanBreadcrumbs(): Executable = {
      val outer = this
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings).flatMap { result =>
            // clears journey history
            journeyService
              .cleanBreadcrumbs(_ => Nil)(context(request), ec)
              .map(_ => result)
          }
      }
    }

    /** Transform result using provided partial function. */
    final def transform(fx: PartialFunction[Result, Result]): Executable = {
      val outer = this
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer
            .execute(settings)
            .map(result => fx.applyOrElse[Result, Result](result, _ => result))
      }
    }

    /** Recover from an error using custom strategy. */
    final def recover(
      recoveryStrategy: PartialFunction[Throwable, Result]
    ): Executable = {
      val outer = this
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings).recover(recoveryStrategy)
      }
    }

    /** Recover from an error using custom strategy. */
    final def recoverWith(
      recoveryStrategy: Request[_] => PartialFunction[Throwable, Future[Result]]
    ): Executable = {
      val outer = this
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings).recoverWith(recoveryStrategy(request))
      }
    }
  }

  /** Collection of DSL-style action builders */
  object actions {

    /**
      * Displays the current state using default [[renderState]] function.
      * If there is no state yet then redirects to the start.
      * @note follow with [[renderUsing]] to change the default renderer.
      */
    final val showCurrentState: Executable = new ShowCurrentState()

    final class ShowCurrentState private[actions] () extends Executable {
      override def execute(
        settings: Settings
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = context(request)
        JourneyController.this.journeyService.currentState.flatMap {
          case Some(sb @ (state, breadcrumbs)) =>
            Future.successful(
              settings.outcomeFactoryWithDefault(JourneyController.this.display)(sb)(request)
            )
          case None =>
            JourneyController.this.redirectToStart
        }
      }
    }

    /**
      * Display if the current state is of type S,
      * otherwise redirect back to the root state.
      *
      * @tparam S type of the expected current state
      *
      * @note to alter show behaviour follow with:
      * - [[orRollback]],
      * - [[orRollbackUsing]],
      * - [[orApply]],
      * - [[orApplyWithRequest]]
      * - [[orRedirectToCurrentState]]
      * - [[orRedirectToStart]]
      * - [[orReturn]]
      * - [[orRedirectTo]]
      */
    final def show[S <: State: ClassTag]: Show[S] =
      new Show[S](
        rollback = false,
        merger = None,
        fallback = JourneyController.this.defaultShowFallback
      )

    final class Show[S <: State: ClassTag] private[actions] (
      rollback: Boolean,
      merger: Option[Merger[S]],
      fallback: Fallback
    ) extends Executable {

      final val defaultOutcomeFactory: OutcomeFactory =
        JourneyController.this.redirectOrDisplayIf[S]

      override def execute(
        settings: Settings
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = context(request)
        val outcomeFactory              = settings.outcomeFactoryWithDefault(defaultOutcomeFactory)
        merger match {
          case None =>
            JourneyController.this
              .showState[S](outcomeFactory, rollback, fallback)

          case Some(m) =>
            JourneyController.this
              .showStateWithRollbackUsingMerger[S](m, outcomeFactory, fallback)
        }
      }

      /**
        * Modify [[show]] behaviour:
        * Try first rollback to the most recent state of type S
        * and display if found,
        * otherwise redirect back to the root state.
        *
        * @note to alter behaviour follow with [[orApply]] or [[orApplyWithRequest]]
        */
      final def orRollback: Show[S] =
        new Show[S](rollback = true, merger = None, fallback = fallback)

      /**
        * Modify [[show]] behaviour:
        * Add reconciliation step using Merger.
        *
        * Display the state requested by the type parameter S.
        * If the current state is not of type S
        * try rollback to the most recent state matching S
        * **and apply merge function to reconcile the new state with the previous state**,
        * or redirect back to the root state.
        *
        * @note to alter behaviour follow with [[orApply]] or [[orApplyWithRequest]]
        */
      final def orRollbackUsing(merger: Merger[S]): Show[S] =
        new Show[S](rollback = true, merger = Some(merger), fallback = fallback)

      /**
        * Modify [[show]] behaviour:
        * Display the journey state requested by the type parameter S.
        * If the current state is not of type S,
        * try rollback to the most recent state matching S,
        * If there exists no matching state S in the history,
        * apply the transition and redirect to the new state.
        * If transition is not allowed then redirect back to the current state.
        * @tparam S type of the state to display
        */
      final def orApply(transition: Transition): OrApply = new OrApply(transition)

      final class OrApply private[actions] (transition: Transition) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          val outcomeFactory              = settings.outcomeFactoryWithDefault(defaultOutcomeFactory)
          merger match {
            case None =>
              JourneyController.this.showStateOrApply[S](transition, outcomeFactory, rollback)

            case Some(m) =>
              JourneyController.this.showStateUsingMergerOrApply[S](m, outcomeFactory)(transition)
          }
        }
      }

      /**
        * Modify [[show]] behaviour:
        * Display the journey state requested by the type parameter S.
        * If the current state is not of type S,
        * try rollback to the most recent state matching S,
        * If there exists no matching state S in the history,
        * apply the transition and redirect to the new state.
        * If transition is not allowed then redirect back to the current state.
        * @tparam S type of the state to display
        */
      final def orApplyWithRequest(transition: Request[_] => Transition): OrApplyWithRequest =
        new OrApplyWithRequest(transition)

      final class OrApplyWithRequest private[actions] (transition: Request[_] => Transition)
          extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          val outcomeFactory              = settings.outcomeFactoryWithDefault(defaultOutcomeFactory)
          merger match {
            case None =>
              JourneyController.this
                .showStateOrApply[S](transition(request), outcomeFactory, rollback)

            case Some(m) =>
              JourneyController.this.showStateUsingMergerOrApply[S](m, outcomeFactory)(
                transition(request)
              )
          }
        }
      }

      /**
        * Modify [[show]] behaviour:
        * Redirect to the current state if not S,
        * instead of using the default show fallback.
        */
      final def orRedirectToCurrentState: Show[S] =
        new Show[S](
          rollback = rollback,
          merger = merger,
          fallback = JourneyController.this.redirectToCurrentStateFallback
        )

      /**
        * Modify [[show]] behaviour:
        * Redirect to the Start state if current state is not of S type,
        * instead of using the default show fallback.
        */
      final def orRedirectToStart: Show[S] =
        new Show[S](
          rollback = rollback,
          merger = merger,
          fallback = JourneyController.this.redirectToStartFallback
        )

      /**
        * Modify [[show]] behaviour:
        * Return the given result if current state is not of S type,
        * instead of using the default show fallback.
        */
      final def orReturn(result: => Result): Show[S] =
        new Show[S](
          rollback = rollback,
          merger = merger,
          fallback = (_, _, _) => Future.successful(result)
        )

      /**
        * Modify [[show]] behaviour:
        * Redirect to the given call if current state is not of S type,
        * instead of using the default show fallback.
        */
      final def orRedirectTo(call: Call): Show[S] =
        new Show[S](
          rollback = rollback,
          merger = merger,
          fallback = (_, _, _) => Future.successful(Results.Redirect(call))
        )
    }

    /**
      * Apply state transition and redirect to the new state.
      *
      * @note to alter behaviour follow with [[redirectOrDisplayIfSame]] or [[redirectOrDisplayIf]]
      */
    final def apply(transition: Transition): Apply = new Apply(transition)

    final class Apply private[actions] (transition: Transition) extends Executable {
      override def execute(
        settings: Settings
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = JourneyController.this.context(request)
        JourneyController.this
          .apply(transition, settings.outcomeFactoryOpt.getOrElse(JourneyController.this.redirect))
      }
    }

    /** Apply state transition and redirect to the new state. */
    final def applyWithRequest(transition: Request[_] => Transition): ApplyWithRequest =
      new ApplyWithRequest(transition)

    final class ApplyWithRequest private[actions] (transition: Request[_] => Transition)
        extends Executable {
      override def execute(
        settings: Settings
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = JourneyController.this.context(request)
        JourneyController.this
          .apply(
            transition(request),
            settings.outcomeFactoryOpt.getOrElse(JourneyController.this.redirect)
          )
      }
    }

    /**
      * Bind the form to the request.
      * If valid, apply the following transition,
      * if not valid, redirect back to the current state with failed form.
      * @tparam Payload form output type
      */
    final def bindForm[Payload](form: Form[Payload]): BindForm[Payload] =
      new BindForm[Payload](form)

    final class BindForm[Payload] private[actions] (form: Form[Payload]) {

      /**
        * Apply the state transition parametrized by the form output
        * and redirect to the URL matching the new state.
        */
      final def apply(transition: Payload => Transition): Apply = new Apply(transition)

      final class Apply private[actions] (transition: Payload => Transition) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.bindForm(
            form,
            transition,
            settings.outcomeFactoryWithDefault(JourneyController.this.redirect),
            redirectToStart
          )
        }
      }

      /**
        * Apply the state transition parametrized by the form output
        * and redirect to the URL matching the new state.
        */
      final def applyWithRequest(
        transition: Request[_] => Payload => Transition
      ): ApplyWithRequest =
        new ApplyWithRequest(transition)

      final class ApplyWithRequest private[actions] (
        transition: Request[_] => Payload => Transition
      ) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.bindForm(
            form,
            transition(request),
            settings.outcomeFactoryWithDefault(JourneyController.this.redirect),
            redirectToStart
          )
        }
      }
    }

    /**
      * Bind the form, derived from the current state, to the request.
      * If valid, apply the following transition,
      * if not valid, redirect back to the current state with failed form.
      * @tparam Payload form output type
      */
    final def bindFormDerivedFromState[Payload](
      form: State => Form[Payload]
    ): BindFormDerivedFromState[Payload] =
      new BindFormDerivedFromState[Payload](form)

    final class BindFormDerivedFromState[Payload] private[actions] (form: State => Form[Payload]) {

      /**
        * Apply the state transition parametrized by the form output
        * and redirect to the URL matching the new state.
        */
      final def apply(transition: Payload => Transition): Apply = new Apply(transition)

      final class Apply private[actions] (transition: Payload => Transition) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.bindFormDerivedFromState(
            form,
            transition,
            settings.outcomeFactoryWithDefault(JourneyController.this.redirect),
            redirectToStart
          )
        }
      }

      /**
        * Apply the state transition parametrized by the form output
        * and redirect to the URL matching the new state.
        */
      final def applyWithRequest(
        transition: Request[_] => Payload => Transition
      ): ApplyWithRequest =
        new ApplyWithRequest(transition)

      final class ApplyWithRequest private[actions] (
        transition: Request[_] => Payload => Transition
      ) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.bindFormDerivedFromState(
            form,
            transition(request),
            settings.outcomeFactoryWithDefault(JourneyController.this.redirect),
            redirectToStart
          )
        }
      }
    }

    /**
      * Parse the JSON body of the request.
      * If valid, apply the following transition,
      * if not valid, return the alternative result.
      * @tparam Entity entity
      */
    final def parseJson[Entity: Reads]: ParseJson[Entity] = new ParseJson[Entity]

    final class ParseJson[Entity: Reads] private[actions] {

      /**
        * Parse request's body as JSON and apply the state transition if success,
        * otherwise return ifFailure result.
        */
      final def apply(transition: Entity => Transition): Apply = new Apply(transition)

      final class Apply private[actions] (transition: Entity => Transition) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.parseJson(
            transition,
            settings.outcomeFactoryWithDefault(JourneyController.this.redirect)
          )
        }
      }

      /**
        * Parse request's body as JSON and apply the state transition if success,
        * otherwise return ifFailure result.
        */
      final def applyWithRequest(transition: Request[_] => Entity => Transition): ApplyWithRequest =
        new ApplyWithRequest(transition)

      final class ApplyWithRequest private[actions] (transition: Request[_] => Entity => Transition)
          extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.parseJson(
            transition(request),
            settings.outcomeFactoryWithDefault(JourneyController.this.redirect)
          )
        }
      }
    }

    /**
      * Wait until the state becomes of S type and display it using default [[renderState]],
      * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
      */
    final def waitForStateAndDisplay[S <: State: ClassTag](timeoutInSeconds: Int): WaitFor[S] =
      new WaitFor[S](timeoutInSeconds)(display)

    /**
      * Wait until the state becomes of S type and display it using custom renderer,
      * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
      */
    final def waitForStateAndDisplayUsing[S <: State: ClassTag](
      timeoutInSeconds: Int,
      renderer: Renderer
    ): WaitFor[S] =
      new WaitFor[S](timeoutInSeconds)(displayUsing(renderer))

    /**
      * Wait until the state becomes of S type and redirect to it,
      * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
      */
    final def waitForStateThenRedirect[S <: State: ClassTag](timeoutInSeconds: Int): WaitFor[S] =
      new WaitFor[S](timeoutInSeconds)(redirect)

    final class WaitFor[S <: State: ClassTag] private[actions] (timeoutInSeconds: Int)(
      outcomeFactory: OutcomeFactory
    ) extends Executable {
      override def execute(
        settings: Settings
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
        waitForUsing(
          _ => Future.failed(new TimeoutException),
          outcomeFactory
        )

      private def waitForUsing(
        ifTimeout: Request[_] => Future[Result],
        outcomeFactory: OutcomeFactory
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = JourneyController.this.context(request)
        val maxTimestamp: Long          = System.nanoTime() + timeoutInSeconds * 1000000000L
        JourneyController.this.waitFor[S](500, maxTimestamp)(outcomeFactory)(ifTimeout)
      }

      /**
        * Wait until the state becomes of S type,
        * or if timeout expires apply the transition and redirect to the new state.
        *
        * @note follow with [[display]] or [[redirectOrDisplayIf]] to display instead of redirecting.
        */
      final def orApplyOnTimeout(transition: Request[_] => Transition): OrApply =
        new OrApply(transition)

      final class OrApply private[actions] (transition: Request[_] => Transition)
          extends Executable {
        override def execute(
          settings: Settings
        )(implicit
          request: Request[_],
          ec: ExecutionContext
        ): Future[Result] =
          waitForUsing(
            { implicit request =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.apply(
                transition(request),
                settings.outcomeFactoryOpt.getOrElse(JourneyController.this.redirect)
              )
            },
            outcomeFactory
          )
      }
    }

    /**
      * Progress only if authorization succeeds.
      * Pass obtained user data to the subsequent operations.
      * @tparam User authorised user information type
      */
    final def whenAuthorised[User](withAuthorised: WithAuthorised[User]): WhenAuthorised[User] =
      new WhenAuthorised[User](withAuthorised)

    final class WhenAuthorised[User] private[actions] (withAuthorised: WithAuthorised[User]) {

      /**
        * Displays the current state using default [[renderState]] function.
        * If there is no state yet then redirects to the start.
        *
        * @note follow with [[renderUsing]] to use different rendering function.
        */
      final val showCurrentState: Executable = new ShowCurrentState()

      final class ShowCurrentState private[actions] () extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { _ =>
            implicit val rc: RequestContext = context(request)
            JourneyController.this.journeyService.currentState.flatMap {
              case Some(sb @ (state, breadcrumbs)) =>
                Future.successful(
                  settings.outcomeFactoryOpt.getOrElse(JourneyController.this.display)(sb)(request)
                )
              case None =>
                JourneyController.this.redirectToStart
            }
          }
      }

      /**
        * Display if the current state is of type S,
        * otherwise use the default show fallback,
        * e.g. redirect back to the root state.
        *
        * @tparam S type of the expected current state
        *
        * @note to alter show behaviour follow with:
        * - [[orRollback]],
        * - [[orRollbackUsing]],
        * - [[orApply]],
        * - [[orApplyWithRequest]]
        * - [[orRedirectToCurrentState]]
        * - [[orRedirectToStart]]
        * - [[orReturn]]
        * - [[orRedirectTo]]
        */
      final def show[S <: State: ClassTag]: Show[S] =
        new Show[S](
          rollback = false,
          merger = None,
          fallback = JourneyController.this.defaultShowFallback
        )

      final class Show[S <: State: ClassTag] private[actions] (
        rollback: Boolean,
        merger: Option[Merger[S]],
        fallback: Fallback
      ) extends Executable {

        final val defaultOutcomeFactory: OutcomeFactory =
          JourneyController.this.redirectOrDisplayIf[S]

        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          withAuthorised(request) { _ =>
            val outcomeFactory = settings.outcomeFactoryWithDefault(defaultOutcomeFactory)
            merger match {
              case None =>
                JourneyController.this
                  .showState[S](outcomeFactory, rollback, fallback)

              case Some(m) =>
                JourneyController.this
                  .showStateWithRollbackUsingMerger[S](m, outcomeFactory, fallback)
            }

          }
        }

        /**
          * Modify [[show]] behaviour:
          * If current state not S,
          * try rollback to the most recent state of type S and display,
          * otherwise redirect back to the root state.
          *
          * @note to alter behaviour follow with [[orApply]] or [[orApplyWithRequest]]
          */
        final def orRollback: Show[S] =
          new Show[S](rollback = true, merger = None, fallback = fallback)

        /**
          * Modify [[show]] behaviour:
          * Add reconciliation step using Merger.
          *
          * Display the state requested by the type parameter S.
          * If the current state is not of type S,
          * then try rollback to the most recent state matching S
          * **and apply merge function to reconcile the new state with the outgoing**,
          * or redirect back to the root state.
          */
        final def orRollbackUsing(merger: Merger[S]): Show[S] =
          new Show[S](rollback = true, merger = Some(merger), fallback = fallback)

        /**
          * Modify [[show]] behaviour:
          * Use fallback transition in case there is no state of type S.
          *
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try rollback to the most recent state matching S,
          * **If there exists no matching state S in the history,
          * apply the transition and redirect to the new state**.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        final def orApply(transition: User => Transition): OrApply = new OrApply(transition)

        final class OrApply private[actions] (transition: User => Transition) extends Executable {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            withAuthorised(request) { user: User =>
              val outcomeFactory = settings.outcomeFactoryWithDefault(defaultOutcomeFactory)
              merger match {
                case None =>
                  JourneyController.this
                    .showStateOrApply[S](transition(user), outcomeFactory, rollback)

                case Some(m) =>
                  JourneyController.this.showStateUsingMergerOrApply[S](m, outcomeFactory)(
                    transition(user)
                  )
              }
            }
          }
        }

        /**
          * Modify [[show]] behaviour:
          * Use fallback transition in case there is no state of type S.
          *
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try rollback to the most recent state matching S,
          * **If there exists no matching state S in the history,
          * apply the transition and redirect to the new state**.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        final def orApplyWithRequest(
          transition: Request[_] => User => Transition
        ): OrApplyWithRequest =
          new OrApplyWithRequest(transition)

        final class OrApplyWithRequest private[actions] (
          transition: Request[_] => User => Transition
        ) extends Executable {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            withAuthorised(request) { user: User =>
              val outcomeFactory = settings.outcomeFactoryWithDefault(defaultOutcomeFactory)
              merger match {
                case None =>
                  JourneyController.this
                    .showStateOrApply[S](transition(request)(user), outcomeFactory, rollback)

                case Some(m) =>
                  JourneyController.this.showStateUsingMergerOrApply[S](m, outcomeFactory)(
                    transition(request)(user)
                  )
              }
            }
          }
        }

        /**
          * Modify [[show]] behaviour:
          * Redirect to the current state if not S,
          * instead of using the default show fallback.
          */
        final def orRedirectToCurrentState: Show[S] =
          new Show[S](
            rollback = rollback,
            merger = merger,
            fallback = JourneyController.this.redirectToCurrentStateFallback
          )

        /**
          * Reset [[show]] behaviour:
          * Redirect to the Start state if current state is not of S type,
          * instead of using the default show fallback.
          */
        final def orRedirectToStart: Show[S] =
          new Show[S](
            rollback = rollback,
            merger = merger,
            fallback = JourneyController.this.redirectToStartFallback
          )

        /**
          * Modify [[show]] behaviour:
          * Return the given result if current state is not of S type,
          * instead of using the default show fallback.
          */
        final def orReturn(result: => Result): Show[S] =
          new Show[S](
            rollback = rollback,
            merger = merger,
            fallback = (_, _, _) => Future.successful(result)
          )

        /**
          * Modify [[show]] behaviour:
          * Redirect to the given call if current state is not of S type,
          * instead of using the default show fallback.
          */
        final def orRedirectTo(call: Call): Show[S] =
          new Show[S](
            rollback = rollback,
            merger = merger,
            fallback = (_, _, _) => Future.successful(Results.Redirect(call))
          )
      }

      /**
        * Apply state transition parametrized by the user information
        * and redirect to the URL matching the new state.
        *
        * @note to alter behaviour follow with [[redirectOrDisplayIfSame]] or [[redirectOrDisplayIf]]
        */
      final def apply(transition: User => Transition): Apply = new Apply(transition)

      final class Apply private[actions] (transition: User => Transition) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { user: User =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this
              .apply(
                transition(user),
                settings.outcomeFactoryOpt.getOrElse(JourneyController.this.redirect)
              )
          }
      }

      /**
        * Apply state transition parametrized by the user information
        * and redirect to the URL matching the new state.
        */
      final def applyWithRequest(transition: Request[_] => User => Transition): ApplyWithRequest =
        new ApplyWithRequest(transition)

      final class ApplyWithRequest private[actions] (transition: Request[_] => User => Transition)
          extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { user: User =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.apply(
              transition(request)(user),
              settings.outcomeFactoryOpt.getOrElse(JourneyController.this.redirect)
            )
          }
      }

      /**
        * Bind the form to the request.
        * If valid, apply the following transitions,
        * if not valid, redirect back to the current state with failed form.
        * @tparam Payload form output type
        */
      final def bindForm[Payload](form: Form[Payload]): BindForm[Payload] =
        new BindForm[Payload](form)

      final class BindForm[Payload] private[actions] (form: Form[Payload]) {

        /**
          * Apply state transition parametrized by the user information and form output
          * and redirect to the URL matching the end state.
          */
        final def apply(transition: User => Payload => Transition): Apply =
          new Apply(transition)

        final class Apply private[actions] (transition: User => Payload => Transition)
            extends Executable {
          override def execute(
            settings: Settings
          )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
            withAuthorised(request) { user: User =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.bindForm(
                form,
                transition(user),
                settings.outcomeFactoryWithDefault(JourneyController.this.redirect),
                redirectToStart
              )
            }
        }

        /**
          * Apply state transition parametrized by the user information and form output
          * and redirect to the URL matching the end state.
          */
        final def applyWithRequest(
          transition: Request[_] => User => Payload => Transition
        ): ApplyWithRequest =
          new ApplyWithRequest(transition)

        final class ApplyWithRequest private[actions] (
          transition: Request[_] => User => Payload => Transition
        ) extends Executable {
          override def execute(
            settings: Settings
          )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
            withAuthorised(request) { user: User =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.bindForm(
                form,
                transition(request)(user),
                settings.outcomeFactoryWithDefault(JourneyController.this.redirect),
                redirectToStart
              )
            }
        }
      }

      /**
        * Bind the form to the request.
        * If valid, apply the following transitions,
        * if not valid, redirect back to the current state with failed form.
        * @tparam Payload form output type
        */
      final def bindFormDerivedFromState[Payload](
        form: State => Form[Payload]
      ): BindFormDerivedFromState[Payload] =
        new BindFormDerivedFromState[Payload](form)

      final class BindFormDerivedFromState[Payload] private[actions] (
        form: State => Form[Payload]
      ) {

        /**
          * Apply state transition parametrized by the user information and form output
          * and redirect to the URL matching the end state.
          */
        final def apply(transition: User => Payload => Transition): Apply =
          new Apply(transition)

        final class Apply private[actions] (transition: User => Payload => Transition)
            extends Executable {
          override def execute(
            settings: Settings
          )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
            withAuthorised(request) { user: User =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.bindFormDerivedFromState(
                form,
                transition(user),
                settings.outcomeFactoryWithDefault(JourneyController.this.redirect),
                redirectToStart
              )
            }
        }

        /**
          * Apply state transition parametrized by the user information and form output
          * and redirect to the URL matching the end state.
          */
        final def applyWithRequest(
          transition: Request[_] => User => Payload => Transition
        ): ApplyWithRequest =
          new ApplyWithRequest(transition)

        final class ApplyWithRequest private[actions] (
          transition: Request[_] => User => Payload => Transition
        ) extends Executable {
          override def execute(
            settings: Settings
          )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
            withAuthorised(request) { user: User =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.bindFormDerivedFromState(
                form,
                transition(request)(user),
                settings.outcomeFactoryWithDefault(JourneyController.this.redirect),
                redirectToStart
              )
            }
        }
      }

      /**
        * Parse the JSON body of the request.
        * If valid, apply the following transition,
        * if not valid, return the alternative result.
        * @tparam Entity entity
        */
      final def parseJson[Entity: Reads](
        ifFailure: Request[_] => Future[Result]
      ): ParseJson[Entity] =
        new ParseJson[Entity](ifFailure)

      final class ParseJson[Entity: Reads] private[actions] (
        ifFailure: Request[_] => Future[Result]
      ) {

        /**
          * Parse request's body as JSON and apply the state transition if success,
          * otherwise return ifFailure result.
          */
        final def apply(transition: User => Entity => Transition): Apply = new Apply(transition)

        final class Apply private[actions] (transition: User => Entity => Transition)
            extends Executable {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] =
            withAuthorised(request) { user: User =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.parseJson(
                transition(user),
                settings.outcomeFactoryWithDefault(JourneyController.this.redirect)
              )
            }
        }

        /**
          * Parse request's body as JSON and apply the state transition if success,
          * otherwise return ifFailure result.
          */
        final def applyWithRequest(
          transition: Request[_] => User => Entity => Transition
        ): ApplyWithRequest =
          new ApplyWithRequest(transition)

        final class ApplyWithRequest private[actions] (
          transition: Request[_] => User => Entity => Transition
        ) extends Executable {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] =
            withAuthorised(request) { user: User =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.parseJson(
                transition(request)(user),
                settings.outcomeFactoryWithDefault(JourneyController.this.redirect)
              )
            }
        }
      }

      /**
        * Wait until the state becomes of S type and display it using default [[renderState]],
        * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
        */
      final def waitForStateAndDisplay[S <: State: ClassTag](timeoutInSeconds: Int): WaitFor[S] =
        new WaitFor[S](timeoutInSeconds)(display)

      /**
        * Wait until the state becomes of S type and display it using custom renderer,
        * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
        */
      final def waitForStateAndDisplayUsing[S <: State: ClassTag](
        timeoutInSeconds: Int,
        renderer: Renderer
      ): WaitFor[S] =
        new WaitFor[S](timeoutInSeconds)(displayUsing(renderer))

      /**
        * Wait until the state becomes of S type and redirect to it,
        * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
        */
      final def waitForStateThenRedirect[S <: State: ClassTag](timeoutInSeconds: Int): WaitFor[S] =
        new WaitFor[S](timeoutInSeconds)(redirect)

      final class WaitFor[S <: State: ClassTag] private[actions] (timeoutInSeconds: Int)(
        outcomeFactory: OutcomeFactory
      ) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          waitForUsing(
            _ => _ => Future.failed(new TimeoutException),
            outcomeFactory
          )

        private def waitForUsing(
          ifTimeout: User => Request[_] => Future[Result],
          outcomeFactory: OutcomeFactory
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { user: User =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            val maxTimestamp: Long          = System.nanoTime() + timeoutInSeconds * 1000000000L
            JourneyController.this.waitFor[S](500, maxTimestamp)(outcomeFactory)(ifTimeout(user))
          }

        /**
          * Wait until the state becomes of S type,
          * or if timeout expires apply the transition and redirect to the new state.
          *
          * @note follow with [[display]] or [[redirectOrDisplayIf]] to display instead of redirecting.
          */
        final def orApplyOnTimeout(transition: Request[_] => User => Transition): OrApply =
          new OrApply(transition)

        final class OrApply private[actions] (transition: Request[_] => User => Transition)
            extends Executable {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            val f = { user: User => implicit request: Request[_] =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.apply(
                transition(request)(user),
                settings.outcomeFactoryOpt.getOrElse(JourneyController.this.redirect)
              )
            }
            waitForUsing(f, outcomeFactory)
          }
        }
      }
    }
  }

  //-------------------------------------------------
  // DEPRECATED STUFF
  //-------------------------------------------------

  /**
    * @deprecated use DSL [[actions.show[ExpectedState]]]
    */
  protected final def actionShowState(
    expectedStates: ExpectedStates
  )(implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val rc: RequestContext = context(request)
      showState(expectedStates)
    }

  /**
    * @deprecated use DSL [[actions.whenAuthorised(withAuthorised).show[ExpectedState]]]
    */
  protected final def actionShowStateWhenAuthorised[User](
    withAuthorised: WithAuthorised[User]
  )(expectedStates: ExpectedStates)(implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val rc: RequestContext = context(request)
      showStateWhenAuthorised(withAuthorised)(expectedStates)
    }
}

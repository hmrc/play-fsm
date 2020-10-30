/*
 * Copyright 2020 HM Revenue & Customs
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
  import journeyService.model.{Merger, State, Transition, TransitionNotAllowed}

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

  type Route        = Request[_] => Result
  type RouteFactory = StateAndBreadcrumbs => Route
  type Renderer     = Request[_] => (State, Breadcrumbs, Option[Form[_]]) => Result

  protected final def is[S <: State: ClassTag](state: State): Boolean =
    implicitly[ClassTag[S]].runtimeClass.isAssignableFrom(state.getClass)

  /** Display the current state using [[renderState]]. */
  protected final val display: RouteFactory = (state: StateAndBreadcrumbs) =>
    (request: Request[_]) => renderState(state._1, state._2, None)(request)

  /** Display the current state using custom renderer. */
  protected final def displayUsing(renderState: Renderer): RouteFactory =
    (state: StateAndBreadcrumbs) =>
      (request: Request[_]) => renderState(request)(state._1, state._2, None)

  /** Redirect to the current state using URL returned by [[getCallFor]]. */
  protected final val redirect: RouteFactory =
    (state: StateAndBreadcrumbs) =>
      (request: Request[_]) => Results.Redirect(getCallFor(state._1)(request))

  /**
    * If the current state is of type S then display using [[renderState]],
    * otherwise redirect using URL returned by [[getCallFor]].
    */
  protected final def redirectOrDisplayIf[S <: State: ClassTag]: RouteFactory = {
    case sb @ (state, breadcrumbs) =>
      if (is[S](state)) display(sb) else redirect(sb)
  }

  /**
    * If the current state is of type S then display using custom renderer,
    * otherwise redirect using URL returned by [[getCallFor]].
    */
  protected final def redirectOrDisplayUsingIf[S <: State: ClassTag](
    renderState: Renderer
  ): RouteFactory = {
    case sb @ (state, breadcrumbs) =>
      if (is[S](state)) displayUsing(renderState)(sb) else redirect(sb)
  }

  /**
    * If the current state is of type S then redirect using URL returned by [[getCallFor]],
    * otherwise display using [[renderState]].
    */
  protected final def displayOrRedirectIf[S <: State: ClassTag]: RouteFactory = {
    case sb @ (state, breadcrumbs) =>
      if (is[S](state)) redirect(sb) else display(sb)
  }

  /**
    * If the current state is of type S then redirect using URL returned by [[getCallFor]],
    * otherwise display using custom renderer.
    */
  protected final def displayUsingOrRedirectIf[S <: State: ClassTag](
    renderState: Renderer
  ): RouteFactory = {
    case sb @ (state, breadcrumbs) =>
      if (is[S](state)) redirect(sb) else displayUsing(renderState)(sb)
  }

  /**
    * If the current state is same as the origin then display using [[renderState]],
    * otherwise redirect using URL returned by [[getCallFor]].
    */
  protected final def redirectOrDisplayIfSame(origin: State): RouteFactory = {
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
  ): RouteFactory = {
    case sb @ (state, breadcrumbs) =>
      if (state == origin) displayUsing(renderState)(sb) else redirect(sb)
  }

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
  protected final def apply(transition: Transition, routeFactory: RouteFactory)(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    journeyService
      .apply(transition)
      .map(routeFactory)
      .map(_(request))
      .recover {
        case TransitionNotAllowed(origin, breadcrumbs, _) =>
          routeFactory((origin, breadcrumbs))(request) // renders current state back
      }

  /** Default fallback result is to redirect back to the Start state. */
  private def redirectToStart(implicit
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

  //-------------------------------------------------
  // STATE RENDERING HELPERS
  //-------------------------------------------------

  type ExpectedStates = PartialFunction[State, Unit]

  /**
    * Display the state requested by the type parameter S.
    * If the current state is not of type S,
    * try to rewind the history back to the most recent state matching S,
    * or redirect back to the root state.
    * @tparam S type of the state to display
    */
  protected final def showState(
    expectedStates: ExpectedStates
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      stateAndBreadcrumbsOpt <- journeyService.currentState
      result <- stateAndBreadcrumbsOpt match {
                  case None => redirectToStart
                  case Some(stateAndBreadcrumbs) =>
                    if (hasState(expectedStates, stateAndBreadcrumbs))
                      journeyService.currentState
                        .flatMap(rewindTo(expectedStates)(redirectToStart))
                    else redirectToStart
                }
    } yield result

  /**
    * Display the state requested by the type parameter S.
    * If the current state is not of type S,
    * try to rewind the history back to the most recent state matching S,
    * or redirect back to the root state.
    *
    * @tparam S type of the state to display
    * @param routeFactory route factory to use
    */
  protected final def showState[S <: State: ClassTag](routeFactory: RouteFactory)(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    for {
      sb <- journeyService.currentState
      result <- sb match {
                  case Some((state, breadcrumbs))
                      if is[S](state) || hasRecentState[S](breadcrumbs) =>
                    rewindTo[S](redirectToStart, routeFactory)(sb)
                  case _ =>
                    redirectToStart
                }

    } yield result

  /**
    * Display the journey state requested by the type parameter S.
    * If the current state is not of type S,
    * try to rewind the history back to the most recent state matching S,
    * If there exists no matching state S in the history,
    * apply the transition and, depending on the outcome,
    * redirect to the new state or display if S.
    * If transition is not allowed then redirect back to the current state.
    * @tparam S type of the state to display
    */
  protected final def showStateOrApply[S <: State: ClassTag](
    transition: Transition,
    routeFactory: RouteFactory
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      sb <- journeyService.currentState
      result <- sb match {
                  case Some((state, breadcrumbs))
                      if is[S](state) || hasRecentState[S](breadcrumbs) =>
                    rewindTo[S](apply(transition, routeFactory), routeFactory)(sb)
                  case _ =>
                    apply(transition, routeFactory)
                }
    } yield result

  /**
    * Display the state requested by the type parameter S.
    * If the current state is not of type S
    * try to rewind the history back to the most recent state matching S
    * and apply merge function to reconcile the new state with the previous state,
    * or redirect back to the root state.
    */
  protected final def showStateUsingMerger[S <: State: ClassTag](
    merger: Merger[S],
    routeFactory: RouteFactory
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      sb <- journeyService.currentState
      result <- sb match {
                  case Some((state, breadcrumbs))
                      if is[S](state) || hasRecentState[S](breadcrumbs) =>
                    rewindAndModify[S](merger.withState(state))(redirectToStart, routeFactory)(sb)
                  case _ =>
                    redirectToStart
                }
    } yield result

  /**
    * Display the journey state requested by the type parameter S.
    * If the current state is not of type S,
    * try to rewind the history back to the most recent state matching S
    * and apply merge function to reconcile the new state with the current state.
    * If there exists no matching state S in the history,
    * apply the transition and, depending on the outcome,
    * redirect to the new state or display if S.
    * If transition is not allowed then redirect back to the current state.
    * @tparam S type of the state to display
    */
  protected final def showStateUsingMergerOrApply[S <: State: ClassTag](
    merger: Merger[S],
    routeFactory: RouteFactory
  )(
    transition: Transition
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      sb <- journeyService.currentState
      result <- sb match {
                  case Some((state, breadcrumbs))
                      if is[S](state) || hasRecentState[S](breadcrumbs) =>
                    rewindAndModify[S](merger.withState(state))(
                      apply(transition, routeFactory),
                      routeFactory
                    )(sb)
                  case _ => apply(transition, routeFactory)
                }
    } yield result

  /**
    * Wait for a state to become of type S at most until maxTimestamp.
    * Check in intervals. If the timeout expires, execute ifTimeout.
    */
  protected final def waitFor[S <: State: ClassTag](
    interval: Long,
    maxTimestamp: Long
  )(routeFactory: RouteFactory)(ifTimeout: Request[_] => Future[Result])(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    journeyService.currentState.flatMap {
      case Some(sb @ (state, _)) if is[S](state) =>
        Future.successful(routeFactory(sb)(request))
      case _ =>
        if (System.nanoTime() > maxTimestamp) ifTimeout(request)
        else
          Schedule(interval) {
            waitFor[S](interval, maxTimestamp)(routeFactory)(ifTimeout)
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

  /** Rewind journey state and history (breadcrumbs) back to the most recent state matching expectation. */
  protected final def rewindTo(expectedState: PartialFunction[State, Unit])(
    fallback: => Future[Result]
  )(
    stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    stateAndBreadcrumbsOpt match {
      case None => fallback
      case Some((state, breadcrumbs)) =>
        if (expectedState.isDefinedAt(state))
          Future.successful(renderState(state, breadcrumbs, None)(request))
        else journeyService.stepBack.flatMap(rewindTo(expectedState)(fallback))
    }

  /** Rewind journey state and history (breadcrumbs) back to the most recent state of type S. */
  protected final def rewindTo[S <: State: ClassTag](
    fallback: => Future[Result],
    routeFactory: RouteFactory
  )(
    stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    stateAndBreadcrumbsOpt match {
      case None => fallback
      case Some(sb @ (state, _)) =>
        if (is[S](state))
          Future.successful(routeFactory(sb)(request))
        else
          journeyService.stepBack.flatMap(rewindTo[S](fallback, routeFactory))
    }

  /**
    * Rewind journey state and history (breadcrumbs) back to the most recent state of type S,
    * and if exists, apply modification.
    */
  private final def rewindAndModify[S <: State: ClassTag](modification: S => S)(
    fallback: => Future[Result],
    routeFactory: RouteFactory
  )(
    stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    stateAndBreadcrumbsOpt match {
      case None => fallback
      case Some((state, _)) =>
        if (is[S](state))
          journeyService
            .modify(modification)
            .map(routeFactory)
            .map(_(request))
        else journeyService.stepBack.flatMap(rewindAndModify(modification)(fallback, routeFactory))
    }

  //-------------------------------------------------
  // FORM BINDING HELPERS
  //-------------------------------------------------

  /**
    * Bind form and apply transition if success,
    * otherwise redirect to the current state with form errors in the flash scope.
    */
  protected final def bindForm[T](
    form: Form[T],
    transition: T => Transition,
    routeFactory: RouteFactory = redirect
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
              redirectToStart
          },
        userInput => apply(transition(userInput), routeFactory)
      )

  /**
    * Parse request's body as JSON and apply transition if success,
    * otherwise return ifFailure result.
    */
  protected final def parseJson[T: Reads](transition: T => Transition, routeFactory: RouteFactory)(
    implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] = {
    val body = request.asInstanceOf[Request[AnyContent]].body
    body.asJson.flatMap(_.asOpt[T]) match {
      case Some(entity) =>
        apply(transition(entity), routeFactory)
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
    routeFactory: RouteFactory
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      apply(transition(user), routeFactory)
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
    routeFactoryOpt: Option[RouteFactory] = None
  ) {

    def setRouteFactory(routeFactory: RouteFactory): Settings =
      copy(routeFactoryOpt = Some(routeFactory))

    def setRouteFactory(routeFactoryOpt2: Option[RouteFactory]): Settings =
      copy(routeFactoryOpt = routeFactoryOpt2)

    def routeFactoryWithDefault(routeFactory: RouteFactory): RouteFactory =
      routeFactoryOpt.getOrElse(routeFactory)
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
      * @param routeFactoryOpt optional override of the routeFactory
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
          outer.execute(settings.setRouteFactory(JourneyController.this.display))
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
          outer.execute(settings.setRouteFactory(JourneyController.this.displayUsing(renderState)))
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
          outer.execute(settings.setRouteFactory(JourneyController.this.redirect))
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
              settings.setRouteFactory(current.map {
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
              settings.setRouteFactory(current.map {
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
      val outer                      = this
      val routeFactory: RouteFactory = JourneyController.this.redirectOrDisplayIf[S]
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setRouteFactory(routeFactory))
      }
    }

    /**
      * Change how the result is created.
      * If the state after action is of type S then display using custom renderer,
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayUsingIf[S <: State: ClassTag](renderState: Renderer): Executable = {
      val outer = this
      val routeFactory: RouteFactory =
        JourneyController.this.redirectOrDisplayUsingIf[S](renderState)
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setRouteFactory(routeFactory))
      }
    }

    /**
      * Change how the result is created.
      * If the state after action is of type S then redirect using URL returned by [[getCallFor]]
      * otherwise display using [[renderState]],.
      */
    final def displayOrRedirectIf[S <: State: ClassTag]: Executable = {
      val outer                      = this
      val routeFactory: RouteFactory = JourneyController.this.displayOrRedirectIf[S]
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setRouteFactory(routeFactory))
      }
    }

    /**
      * Change how the result is created.
      * If the state after action is of type S then redirect using URL returned by [[getCallFor]],
      * otherwise display using custom renderer.
      */
    final def displayUsingOrRedirectIf[S <: State: ClassTag](renderState: Renderer): Executable = {
      val outer = this
      val routeFactory: RouteFactory =
        JourneyController.this.displayUsingOrRedirectIf[S](renderState)
      new Executable {
        def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute(settings.setRouteFactory(routeFactory))
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
    val showCurrentState: Executable = new ShowCurrentState()

    final class ShowCurrentState private[actions] () extends Executable {
      override def execute(
        settings: Settings
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = context(request)
        JourneyController.this.journeyService.currentState.flatMap {
          case Some(sb @ (state, breadcrumbs)) =>
            Future.successful(
              settings.routeFactoryWithDefault(JourneyController.this.display)(sb)(request)
            )
          case None =>
            JourneyController.this.redirectToStart
        }
      }
    }

    /**
      * Display the state requested by the type parameter S.
      * If the current state is not of type S,
      * try to rewind the history back to the most recent state matching S,
      * or redirect back to the root state.
      *
      * @note to alter behaviour follow with [[using]], [[orApply]] or [[orApplyWithRequest]]
      *
      * @tparam S type of the state to display
      */
    def show[S <: State: ClassTag]: Show[S] = new Show[S]

    final class Show[S <: State: ClassTag] private[actions] () extends Executable {

      val defaultRouteFactory: RouteFactory =
        JourneyController.this.redirectOrDisplayIf[S]

      override def execute(
        settings: Settings
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = context(request)
        JourneyController.this.showState[S](
          settings.routeFactoryWithDefault(JourneyController.this.display)
        )
      }

      /**
        * Display the journey state requested by the type parameter S.
        * If the current state is not of type S,
        * try to rewind the history back to the most recent state matching S,
        * If there exists no matching state S in the history,
        * apply the transition and redirect to the new state.
        * If transition is not allowed then redirect back to the current state.
        * @tparam S type of the state to display
        */
      def orApply(transition: Transition): OrApply = new OrApply(transition)

      final class OrApply private[actions] (transition: Transition) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this
            .showStateOrApply[S](transition, settings.routeFactoryWithDefault(defaultRouteFactory))
        }
      }

      /**
        * Display the journey state requested by the type parameter S.
        * If the current state is not of type S,
        * try to rewind the history back to the most recent state matching S,
        * If there exists no matching state S in the history,
        * apply the transition and redirect to the new state.
        * If transition is not allowed then redirect back to the current state.
        * @tparam S type of the state to display
        */
      def orApplyWithRequest(transition: Request[_] => Transition): OrApplyWithRequest =
        new OrApplyWithRequest(transition)

      final class OrApplyWithRequest private[actions] (transition: Request[_] => Transition)
          extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.showStateOrApply[S](
            transition(request),
            settings.routeFactoryWithDefault(defaultRouteFactory)
          )
        }
      }

      /**
        * Add reconciliation step using Merger.
        *
        * Display the state requested by the type parameter S.
        * If the current state is not of type S
        * try to rewind the history back to the most recent state matching S
        * **and apply merge function to reconcile the new state with the previous state**,
        * or redirect back to the root state.
        */
      def using(merger: Merger[S]): UsingMerger = new UsingMerger(merger)

      final class UsingMerger private[actions] (merger: Merger[S]) extends Executable {

        val defaultRouteFactory: RouteFactory =
          JourneyController.this.redirectOrDisplayIf[S]

        override def execute(
          settings: Settings
        )(implicit
          request: Request[_],
          ec: ExecutionContext
        ): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this
            .showStateUsingMerger[S](
              merger,
              settings.routeFactoryWithDefault(defaultRouteFactory)
            )
        }

        /**
          * Use fallback transition in case there is no state of type S.
          *
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try to rewind the history back to the most recent state matching S
          * and apply merge function to reconcile the new state with the current state.
          * **If there exists no matching state S in the history,
          * apply the transition and redirect to the new state**.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        def orApply(transition: Transition): OrApply = new OrApply(transition)

        final class OrApply private[actions] (transition: Transition) extends Executable {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.showStateUsingMergerOrApply[S](
              merger,
              settings.routeFactoryWithDefault(defaultRouteFactory)
            )(
              transition
            )
          }
        }

        /**
          * Use fallback transition in case there is no state of type S.
          *
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try to rewind the history back to the most recent state matching S
          * and apply merge function to reconcile the new state with the current state.
          * **If there exists no matching state S in the history,
          * apply the transition and redirect to the new state**.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        def orApplyWithRequest(transition: Request[_] => Transition): OrApplyWithRequest =
          new OrApplyWithRequest(transition)

        final class OrApplyWithRequest private[actions] (transition: Request[_] => Transition)
            extends Executable {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.showStateUsingMergerOrApply[S](
              merger,
              settings.routeFactoryWithDefault(defaultRouteFactory)
            )(
              transition(request)
            )
          }
        }
      }
    }

    /**
      * Apply state transition and redirect to the new state.
      *
      * @note to alter behaviour follow with [[redirectOrDisplayIfSame]] or [[redirectOrDisplayIf]]
      */
    def apply(transition: Transition): Apply = new Apply(transition)

    final class Apply private[actions] (transition: Transition) extends Executable {
      override def execute(
        settings: Settings
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = JourneyController.this.context(request)
        JourneyController.this
          .apply(transition, settings.routeFactoryOpt.getOrElse(JourneyController.this.redirect))
      }
    }

    /** Apply state transition and redirect to the new state. */
    def applyWithRequest(transition: Request[_] => Transition): ApplyWithRequest =
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
            settings.routeFactoryOpt.getOrElse(JourneyController.this.redirect)
          )
      }
    }

    /**
      * Bind the form to the request.
      * If valid, apply the following transition,
      * if not valid, redirect back to the current state with failed form.
      * @tparam Payload form output type
      */
    def bindForm[Payload](form: Form[Payload]): BindForm[Payload] =
      new BindForm[Payload](form)

    final class BindForm[Payload] private[actions] (form: Form[Payload]) {

      /**
        * Apply the state transition parametrized by the form output
        * and redirect to the URL matching the new state.
        */
      def apply(transition: Payload => Transition): Apply = new Apply(transition)

      final class Apply private[actions] (transition: Payload => Transition) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.bindForm(
            form,
            transition,
            settings.routeFactoryWithDefault(JourneyController.this.redirect)
          )
        }
      }

      /**
        * Apply the state transition parametrized by the form output
        * and redirect to the URL matching the new state.
        */
      def applyWithRequest(transition: Request[_] => Payload => Transition): ApplyWithRequest =
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
            settings.routeFactoryWithDefault(JourneyController.this.redirect)
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
    def parseJson[Entity: Reads]: ParseJson[Entity] = new ParseJson[Entity]

    final class ParseJson[Entity: Reads] private[actions] {

      /**
        * Parse request's body as JSON and apply the state transition if success,
        * otherwise return ifFailure result.
        */
      def apply(transition: Entity => Transition): Apply = new Apply(transition)

      final class Apply private[actions] (transition: Entity => Transition) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.parseJson(
            transition,
            settings.routeFactoryWithDefault(JourneyController.this.redirect)
          )
        }
      }

      /**
        * Parse request's body as JSON and apply the state transition if success,
        * otherwise return ifFailure result.
        */
      def applyWithRequest(transition: Request[_] => Entity => Transition): ApplyWithRequest =
        new ApplyWithRequest(transition)

      final class ApplyWithRequest private[actions] (transition: Request[_] => Entity => Transition)
          extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.parseJson(
            transition(request),
            settings.routeFactoryWithDefault(JourneyController.this.redirect)
          )
        }
      }
    }

    /**
      * Wait until the state becomes of S type and display it,
      * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
      */
    def waitForStateAndDisplay[S <: State: ClassTag](timeoutInSeconds: Int): WaitFor[S] =
      new WaitFor[S](timeoutInSeconds)(display)

    /**
      * Wait until the state becomes of S type and redirect to it,
      * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
      */
    def waitForStateThenRedirect[S <: State: ClassTag](timeoutInSeconds: Int): WaitFor[S] =
      new WaitFor[S](timeoutInSeconds)(redirect)

    final class WaitFor[S <: State: ClassTag] private[actions] (timeoutInSeconds: Int)(
      routeFactory: RouteFactory
    ) extends Executable {
      override def execute(
        settings: Settings
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
        waitForUsing(_ => Future.failed(new TimeoutException))

      private def waitForUsing(
        ifTimeout: Request[_] => Future[Result]
      )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = JourneyController.this.context(request)
        val maxTimestamp: Long          = System.nanoTime() + timeoutInSeconds * 1000000000L
        JourneyController.this.waitFor[S](500, maxTimestamp)(routeFactory)(ifTimeout)
      }

      /**
        * Wait until the state becomes of S type,
        * or if timeout expires apply the transition and redirect to the new state.
        *
        * @note follow with [[display]] or [[redirectOrDisplayIf]] to display instead of redirecting.
        */
      def orApplyOnTimeout(transition: Request[_] => Transition): OrApply =
        new OrApply(transition)

      final class OrApply private[actions] (transition: Request[_] => Transition)
          extends Executable {
        override def execute(
          settings: Settings
        )(implicit
          request: Request[_],
          ec: ExecutionContext
        ): Future[Result] =
          waitForUsing { implicit request =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.apply(
              transition(request),
              settings.routeFactoryOpt.getOrElse(JourneyController.this.redirect)
            )
          }

      }
    }

    /**
      * Progress only if authorization succeeds.
      * Pass obtained user data to the subsequent operations.
      * @tparam User authorised user information type
      */
    def whenAuthorised[User](withAuthorised: WithAuthorised[User]): WhenAuthorised[User] =
      new WhenAuthorised[User](withAuthorised)

    final class WhenAuthorised[User] private[actions] (withAuthorised: WithAuthorised[User]) {

      /**
        * Displays the current state using default [[renderState]] function.
        * If there is no state yet then redirects to the start.
        *
        * @note follow with [[renderUsing]] to use different rendering function.
        */
      val showCurrentState: Executable = new ShowCurrentState()

      final class ShowCurrentState private[actions] () extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { _ =>
            implicit val rc: RequestContext = context(request)
            JourneyController.this.journeyService.currentState.flatMap {
              case Some(sb @ (state, breadcrumbs)) =>
                Future.successful(
                  settings.routeFactoryOpt.getOrElse(JourneyController.this.display)(sb)(request)
                )
              case None =>
                JourneyController.this.redirectToStart
            }
          }
      }

      /**
        * Display the state requested by the type parameter S.
        * If the current state is not of type S,
        * try to rewind the history back to the most recent state matching S,
        * or redirect back to the root state.
        *
        * @note to alter behaviour follow with [[using]], [[orApply]] or [[orApplyWithRequest]]
        *
        * @tparam S type of the state to display
        */
      def show[S <: State: ClassTag]: Show[S] = new Show[S]

      final class Show[S <: State: ClassTag] private[actions] () extends Executable {

        val defaultRouteFactory: RouteFactory =
          JourneyController.this.redirectOrDisplayIf[S]

        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          withAuthorised(request) { _ =>
            JourneyController.this.showState[S](
              settings.routeFactoryWithDefault(JourneyController.this.display)
            )
          }
        }

        /**
          * Use fallback transition in case there is no state of type S.
          *
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try to rewind the history back to the most recent state matching S,
          * **If there exists no matching state S in the history,
          * apply the transition and redirect to the new state**.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        def orApply(transition: User => Transition): OrApply = new OrApply(transition)

        final class OrApply private[actions] (transition: User => Transition) extends Executable {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            withAuthorised(request) { user: User =>
              JourneyController.this.showStateOrApply[S](
                transition(user),
                settings.routeFactoryWithDefault(defaultRouteFactory)
              )
            }
          }
        }

        /**
          * Use fallback transition in case there is no state of type S.
          *
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try to rewind the history back to the most recent state matching S,
          * **If there exists no matching state S in the history,
          * apply the transition and redirect to the new state**.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        def orApplyWithRequest(transition: Request[_] => User => Transition): OrApplyWithRequest =
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
              JourneyController.this.showStateOrApply[S](
                transition(request)(user),
                settings.routeFactoryWithDefault(defaultRouteFactory)
              )
            }
          }
        }

        /**
          * Add reconciliation step using Merger.
          *
          * Display the state requested by the type parameter S.
          * If the current state is not of type S,
          * then try to rewind history back to the most recent state matching S
          * **and apply merge function to reconcile the new state with the outgoing**,
          * or redirect back to the root state.
          */
        def using(merge: Merger[S]): UsingMerger = new UsingMerger(merge)

        final class UsingMerger private[actions] (merger: Merger[S]) extends Executable {

          val defaultRouteFactory: RouteFactory =
            JourneyController.this.redirectOrDisplayIf[S]

          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            withAuthorised(request) { user: User =>
              JourneyController.this.showStateUsingMerger[S](
                merger,
                settings.routeFactoryWithDefault(defaultRouteFactory)
              )
            }
          }

          /**
            * Display the journey state requested by the type parameter S.
            * If the current state is not of type S,
            * try to rewind the history back to the most recent state matching S
            * and apply merge function to reconcile the new state with the current state.
            * If there exists no matching state S in the history,
            * apply the transition and redirect to the new state.
            * If transition is not allowed then redirect back to the current state.
            * @tparam S type of the state to display
            */
          def orApply(transition: User => Transition): OrApply =
            new OrApply(transition)

          final class OrApply private[actions] (transition: User => Transition) extends Executable {
            override def execute(
              settings: Settings
            )(implicit
              request: Request[_],
              ec: ExecutionContext
            ): Future[Result] = {
              implicit val rc: RequestContext = JourneyController.this.context(request)
              withAuthorised(request) { user: User =>
                JourneyController.this.showStateUsingMergerOrApply[S](
                  merger,
                  settings.routeFactoryWithDefault(defaultRouteFactory)
                )(
                  transition(user)
                )
              }
            }
          }

          /**
            * Display the journey state requested by the type parameter S.
            * If the current state is not of type S,
            * try to rewind the history back to the most recent state matching S
            * and apply merge function to reconcile the new state with the current state.
            * If there exists no matching state S in the history,
            * apply the transition and redirect to the new state.
            * If transition is not allowed then redirect back to the current state.
            * @tparam S type of the state to display
            */
          def orApplyWithRequest(transition: Request[_] => User => Transition): OrApplyWithRequest =
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
                JourneyController.this.showStateUsingMergerOrApply[S](
                  merger,
                  settings.routeFactoryWithDefault(defaultRouteFactory)
                )(
                  transition(request)(user)
                )
              }
            }
          }

        }
      }

      /**
        * Apply state transition parametrized by the user information
        * and redirect to the URL matching the new state.
        *
        * @note to alter behaviour follow with [[redirectOrDisplayIfSame]] or [[redirectOrDisplayIf]]
        */
      def apply(transition: User => Transition): Apply = new Apply(transition)

      final class Apply private[actions] (transition: User => Transition) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { user: User =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this
              .apply(
                transition(user),
                settings.routeFactoryOpt.getOrElse(JourneyController.this.redirect)
              )
          }
      }

      /**
        * Apply state transition parametrized by the user information
        * and redirect to the URL matching the new state.
        */
      def applyWithRequest(transition: Request[_] => User => Transition): ApplyWithRequest =
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
              settings.routeFactoryOpt.getOrElse(JourneyController.this.redirect)
            )
          }
      }

      /**
        * Bind the form to the request.
        * If valid, apply the following transitions,
        * if not valid, redirect back to the current state with failed form.
        * @tparam Payload form output type
        */
      def bindForm[Payload](form: Form[Payload]): BindForm[Payload] =
        new BindForm[Payload](form)

      final class BindForm[Payload] private[actions] (form: Form[Payload]) {

        /**
          * Apply state transition parametrized by the user information and form output
          * and redirect to the URL matching the end state.
          */
        def apply(transition: User => Payload => Transition): Apply =
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
                settings.routeFactoryWithDefault(JourneyController.this.redirect)
              )
            }
        }

        /**
          * Apply state transition parametrized by the user information and form output
          * and redirect to the URL matching the end state.
          */
        def applyWithRequest(
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
                settings.routeFactoryWithDefault(JourneyController.this.redirect)
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
      def parseJson[Entity: Reads](ifFailure: Request[_] => Future[Result]): ParseJson[Entity] =
        new ParseJson[Entity](ifFailure)

      final class ParseJson[Entity: Reads] private[actions] (
        ifFailure: Request[_] => Future[Result]
      ) {

        /**
          * Parse request's body as JSON and apply the state transition if success,
          * otherwise return ifFailure result.
          */
        def apply(transition: User => Entity => Transition): Apply = new Apply(transition)

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
                settings.routeFactoryWithDefault(JourneyController.this.redirect)
              )
            }
        }

        /**
          * Parse request's body as JSON and apply the state transition if success,
          * otherwise return ifFailure result.
          */
        def applyWithRequest(
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
                settings.routeFactoryWithDefault(JourneyController.this.redirect)
              )
            }
        }
      }

      /**
        * Wait until the state becomes of S type and display it,
        * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
        */
      def waitForStateAndDisplay[S <: State: ClassTag](timeoutInSeconds: Int): WaitFor[S] =
        new WaitFor[S](timeoutInSeconds)(display)

      /**
        * Wait until the state becomes of S type and redirect to it,
        * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
        */
      def waitForStateThenRedirect[S <: State: ClassTag](timeoutInSeconds: Int): WaitFor[S] =
        new WaitFor[S](timeoutInSeconds)(redirect)

      final class WaitFor[S <: State: ClassTag] private[actions] (timeoutInSeconds: Int)(
        routeFactory: RouteFactory
      ) extends Executable {
        override def execute(
          settings: Settings
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          waitForUsing(_ => _ => Future.failed(new TimeoutException))

        private def waitForUsing(
          ifTimeout: User => Request[_] => Future[Result]
        )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { user: User =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            val maxTimestamp: Long          = System.nanoTime() + timeoutInSeconds * 1000000000L
            JourneyController.this.waitFor[S](500, maxTimestamp)(routeFactory)(ifTimeout(user))
          }

        /**
          * Wait until the state becomes of S type,
          * or if timeout expires apply the transition and redirect to the new state.
          *
          * @note follow with [[display]] or [[redirectOrDisplayIf]] to display instead of redirecting.
          */
        def orApplyOnTimeout(transition: Request[_] => User => Transition): OrApply =
          new OrApply(transition)

        final class OrApply private[actions] (transition: Request[_] => User => Transition)
            extends Executable {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] =
            waitForUsing { user => implicit request =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.apply(
                transition(request)(user),
                settings.routeFactoryOpt.getOrElse(JourneyController.this.redirect)
              )
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

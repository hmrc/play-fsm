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
import play.api.data.FormBinding

/**
  * Controller mixin for journeys using [[JourneyModel]].
  * Provides rich set of `actions.` DSL.
  *
  * Final controller class should override these extension points:
  *
  *   - [[journeyService]]: journey service instance
  *   - [[actionBuilder]]: Play helper for creating [[Action]] values
  *   - [[getCallFor]]: function mapping states to endpoints
  *   - [[renderState]]: function mapping states to views
  *   - [[context]]: function transforming request into a custom context object
  *   - [[withValidRequest]]: http request interceptor and validator
  *
  * Consider using together with [[JourneyIdSupport]].
  *
  * @tparam RequestContext type of the custom context object available implicitly across all actions
  */
trait JourneyController[RequestContext] {

  import journeyService.{Breadcrumbs, StateAndBreadcrumbs}
  import journeyService.model
  import model.{Merger, State, StayInCurrentState, Transition, TransitionNotAllowed}

  //-------------------------------------------------
  // EXTENSION POINTS
  //-------------------------------------------------

  /** Service providing state management functions. */
  val journeyService: JourneyService[RequestContext]

  /** Play helper for creating [[Action]] values. */
  val actionBuilder: ActionBuilder[Request, AnyContent]

  /**
    * Function mapping FSM states to the endpoint calls.
    * This function is invoked internally when the result of an action is
    * to *redirect* to some state.
    */
  def getCallFor(state: State)(implicit request: Request[_]): Call

  /**
    * Function mapping FSM states to Play results: views or redirections.
    * This function is invoked internally when the result of an action is
    * to *display* some state.
    */
  def renderState(state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result

  /** Implement to provide function transforming request into a custom context object. * */
  def context(implicit rh: RequestHeader): RequestContext

  /** Override to do basic checks on every incoming request (headers, session, etc.). */
  def withValidRequest(
    body: => Future[Result]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    body

  //-------------------------------------------------
  // INTERNAL TYPE ALIASES
  //-------------------------------------------------

  type Outcome              = Request[_] => Future[Result]
  type OutcomeFactory       = StateAndBreadcrumbs => Outcome
  type Renderer             = Request[_] => (State, Breadcrumbs, Option[Form[_]]) => Result
  type AsyncRenderer        = Request[_] => (State, Breadcrumbs, Option[Form[_]]) => Future[Result]
  type Fallback             = (RequestContext, Request[_], ExecutionContext) => Future[Result]
  type WithAuthorised[User] = Request[_] => (User => Future[Result]) => Future[Result]

  object Renderer {
    final def simple(f: PartialFunction[State, Result]): Renderer = {
      (request: Request[_]) =>
        (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
          f.applyOrElse(state, (_: State) => play.api.mvc.Results.NotImplemented)
    }

    final def withRequest(f: Request[_] => PartialFunction[State, Result]): Renderer = {
      (request: Request[_]) =>
        (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
          f(request).applyOrElse(state, (_: State) => play.api.mvc.Results.NotImplemented)
    }

    final def withRequestAndForm(
      f: Request[_] => Option[Form[_]] => PartialFunction[State, Result]
    ): Renderer = {
      (request: Request[_]) =>
        (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
          f(request)(formWithErrors)(state)
    }

    final def apply(
      f: Request[_] => Breadcrumbs => Option[Form[_]] => PartialFunction[State, Result]
    ): Renderer = {
      (request: Request[_]) =>
        (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
          f(request)(breadcrumbs)(formWithErrors)(state)
    }
  }

  object AsyncRenderer {
    final def simple(f: PartialFunction[State, Future[Result]]): AsyncRenderer = {
      (request: Request[_]) =>
        (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
          f(state)
    }

    final def withRequest(
      f: Request[_] => PartialFunction[State, Future[Result]]
    ): AsyncRenderer = {
      (request: Request[_]) =>
        (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
          f(request)(state)
    }

    final def withRequestAndForm(
      f: Request[_] => Option[Form[_]] => PartialFunction[State, Future[Result]]
    ): AsyncRenderer = {
      (request: Request[_]) =>
        (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
          f(request)(formWithErrors)(state)
    }

    final def apply(
      f: Request[_] => Breadcrumbs => Option[Form[_]] => PartialFunction[State, Future[Result]]
    ): AsyncRenderer = {
      (request: Request[_]) =>
        (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
          f(request)(breadcrumbs)(formWithErrors)(state)
    }
  }

  //-------------------------------------------------
  // BACKLINK HELPERS
  //-------------------------------------------------

  /**
    * Returns a call to the most recent state found in breadcrumbs,
    * otherwise returns a call to the root state.
    */
  final def backLinkFor(breadcrumbs: Breadcrumbs)(implicit request: Request[_]): Call =
    breadcrumbs.headOption
      .map(getCallFor)
      .getOrElse(getCallFor(journeyService.model.root))

  /**
    * Returns a call to the latest state of S type if exists,
    * otherwise returns a fallback call if defined,
    * or a call to the root state.
    */
  final def backLinkToMostRecent[S <: State: ClassTag](
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

  /** FSM controller helper methods. */
  final object helpers {

    final def is[S <: State: ClassTag](state: State): Boolean =
      implicitly[ClassTag[S]].runtimeClass.isAssignableFrom(state.getClass)

    //-------------------------------------------------
    // POSSIBLE ACTION OUTCOMES
    //-------------------------------------------------

    /** Display the current state using [[renderState]]. */
    final val display: OutcomeFactory = (state: StateAndBreadcrumbs) =>
      (request: Request[_]) => Future.successful(renderState(state._1, state._2, None)(request))

    /** Display the current state using custom renderer. */
    final def displayUsing(renderState: Renderer): OutcomeFactory =
      (state: StateAndBreadcrumbs) =>
        (request: Request[_]) => Future.successful(renderState(request)(state._1, state._2, None))

    /** Display the current state using custom asynchronous renderer. */
    final def displayAsyncUsing(renderState: AsyncRenderer): OutcomeFactory =
      (state: StateAndBreadcrumbs) =>
        (request: Request[_]) => renderState(request)(state._1, state._2, None)

    /** Redirect to the current state using URL returned by [[getCallFor]]. */
    final val redirect: OutcomeFactory =
      (state: StateAndBreadcrumbs) =>
        (request: Request[_]) => Future.successful(Results.Redirect(getCallFor(state._1)(request)))

    /**
      * If the current state is of type S then display using [[renderState]],
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayIf[S <: State: ClassTag]: OutcomeFactory = {
      case sb @ (state, breadcrumbs) =>
        if (is[S](state)) display(sb) else redirect(sb)
    }

    /**
      * If the current state is of type S then display using custom renderer,
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayUsingIf[S <: State: ClassTag](
      renderState: Renderer
    ): OutcomeFactory = {
      case sb @ (state, breadcrumbs) =>
        if (is[S](state)) displayUsing(renderState)(sb) else redirect(sb)
    }

    /**
      * If the current state is of type S then display using custom asynchronous renderer,
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayAsyncUsingIf[S <: State: ClassTag](
      renderState: AsyncRenderer
    ): OutcomeFactory = {
      case sb @ (state, breadcrumbs) =>
        if (is[S](state)) displayAsyncUsing(renderState)(sb) else redirect(sb)
    }

    /**
      * If the current state is of type S then redirect using URL returned by [[getCallFor]],
      * otherwise display using [[renderState]].
      */
    final def displayOrRedirectIf[S <: State: ClassTag]: OutcomeFactory = {
      case sb @ (state, breadcrumbs) =>
        if (is[S](state)) redirect(sb) else display(sb)
    }

    /**
      * If the current state is of type S then redirect using URL returned by [[getCallFor]],
      * otherwise display using custom renderer.
      */
    final def displayUsingOrRedirectIf[S <: State: ClassTag](
      renderState: Renderer
    ): OutcomeFactory = {
      case sb @ (state, breadcrumbs) =>
        if (is[S](state)) redirect(sb) else displayUsing(renderState)(sb)
    }

    /**
      * If the current state is of type S then redirect using URL returned by [[getCallFor]],
      * otherwise display using custom asynchronous renderer.
      */
    final def displayAsyncUsingOrRedirectIf[S <: State: ClassTag](
      renderState: AsyncRenderer
    ): OutcomeFactory = {
      case sb @ (state, breadcrumbs) =>
        if (is[S](state)) redirect(sb) else displayAsyncUsing(renderState)(sb)
    }

    /**
      * If the current state is same as the origin then display using [[renderState]],
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayIfSame(origin: State): OutcomeFactory = {
      case sb @ (state, breadcrumbs) =>
        if (state == origin) display(sb) else redirect(sb)
    }

    /**
      * If the current state is same as the origin then display using custom renderer,
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayUsingIfSame(
      origin: State,
      renderState: Renderer
    ): OutcomeFactory = {
      case sb @ (state, breadcrumbs) =>
        if (state == origin) displayUsing(renderState)(sb) else redirect(sb)
    }

    /**
      * If the current state is same as the origin then display using custom asynchronous renderer,
      * otherwise redirect using URL returned by [[getCallFor]].
      */
    final def redirectOrDisplayAsyncUsingIfSame(
      origin: State,
      renderState: AsyncRenderer
    ): OutcomeFactory = {
      case sb @ (state, breadcrumbs) =>
        if (state == origin) displayAsyncUsing(renderState)(sb) else redirect(sb)
    }

    //-------------------------------------------------
    // TRANSITION HELPERS
    //-------------------------------------------------

    /**
      * Apply state transition and redirect to the URL matching the new state.
      */
    final def apply(transition: Transition, outcomeFactory: OutcomeFactory)(implicit
      rc: RequestContext,
      request: Request[_],
      ec: ExecutionContext
    ): Future[Result] =
      journeyService
        .apply(transition)
        .map(outcomeFactory)
        .flatMap(_(request))
        .recoverWith {
          case TransitionNotAllowed(origin, breadcrumbs, _) =>
            outcomeFactory((origin, breadcrumbs))(request)

          case StayInCurrentState =>
            journeyService.currentState.flatMap {
              case Some((state, breadcrumbs)) =>
                outcomeFactory((state, breadcrumbs))(request)
              case None =>
                outcomeFactory((journeyService.model.root, Nil))(request)
            }
        }

    /** Default fallback result is to redirect back to the Start state. */
    final def redirectToStart(implicit
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
    final def redirectToCurrentState(implicit
      rc: RequestContext,
      request: Request[_],
      ec: ExecutionContext
    ): Future[Result] =
      journeyService.currentState
        .flatMap {
          case Some(sb) =>
            redirect(sb)(request)

          case None =>
            apply(journeyService.model.start, redirect)
        }

    final val redirectToCurrentStateFallback: Fallback = redirectToCurrentState(_, _, _)

    /** Override to change the default behaviour of the [[actions.show[State]]]. */
    val defaultShowFallback: Fallback = redirectToStartFallback

    //-------------------------------------------------
    // STATE RENDERING HELPERS
    //-------------------------------------------------

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
    final def showState[S <: State: ClassTag](
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
                      outcomeFactory(sb)(request)

                    case Some((state, breadcrumbs))
                        if `rollback` && hasRecentState[S](breadcrumbs) =>
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
    final def showStateOrApply[S <: State: ClassTag](
      transition: Transition,
      outcomeFactory: OutcomeFactory,
      rollback: Boolean
    )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
      for {
        sbopt <- journeyService.currentState
        result <- sbopt match {
                    case Some(sb @ (state, breadcrumbs)) if is[S](state) =>
                      outcomeFactory(sb)(request)

                    case Some((state, breadcrumbs))
                        if `rollback` && hasRecentState[S](breadcrumbs) =>
                      rollbackTo[S](apply(transition, outcomeFactory)(_, _, _), outcomeFactory)(
                        sbopt
                      )

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
    final def showStateWithRollbackUsingMerger[S <: State: ClassTag](
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
    final def showStateUsingMergerOrApply[S <: State: ClassTag](
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
    final def waitFor[S <: State: ClassTag](
      intervalInMiliseconds: Long,
      timeoutNanoTime: Long
    )(outcomeFactory: OutcomeFactory)(ifTimeout: Request[_] => Future[Result])(implicit
      rc: RequestContext,
      request: Request[_],
      ec: ExecutionContext,
      scheduler: akka.actor.Scheduler
    ): Future[Result] =
      journeyService.currentState.flatMap {
        case Some(sb @ (state, _)) if is[S](state) =>
          outcomeFactory(sb)(request)
        case _ =>
          if (System.nanoTime() > timeoutNanoTime) ifTimeout(request)
          else
            ScheduleAfter(intervalInMiliseconds) {
              waitFor[S](intervalInMiliseconds * 2, timeoutNanoTime)(outcomeFactory)(ifTimeout)
            }
      }

    /** Check if state of type S exists in the journey history (breadcrumbs). */
    final def hasRecentState[S <: State: ClassTag](breadcrumbs: Breadcrumbs): Boolean =
      breadcrumbs.exists(is[S])

    /** Rollback journey state and history (breadcrumbs) back to the most recent state of type S. */
    final def rollbackTo[S <: State: ClassTag](
      fallback: Fallback,
      outcomeFactory: OutcomeFactory
    )(
      stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs]
    )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
      stateAndBreadcrumbsOpt match {
        case None => fallback(rc, request, ec)
        case Some(sb @ (state, _)) =>
          if (is[S](state))
            outcomeFactory(sb)(request)
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
              .flatMap(_(request))
          else
            journeyService.stepBack.flatMap(
              rollbackAndModify(modification)(fallback, outcomeFactory)
            )
      }

    //-------------------------------------------------
    // BINDING HELPERS
    //-------------------------------------------------

    /**
      * Bind form and apply transition if success,
      * otherwise redirect to the current state with form errors in the flash scope.
      */
    final def bindForm[T](
      form: Form[T],
      transition: T => Transition
    )(implicit
      rc: RequestContext,
      request: Request[_],
      ec: ExecutionContext,
      formBinding: FormBinding
    ): Future[Result] =
      bindForm(form, transition, redirect, redirectToStart)

    /**
      * Bind form and apply transition if success,
      * otherwise redirect to the current state with form errors in the flash scope.
      */
    final def bindForm[T](
      form: Form[T],
      transition: T => Transition,
      outcomeFactory: OutcomeFactory,
      fallback: => Future[Result]
    )(implicit
      rc: RequestContext,
      request: Request[_],
      ec: ExecutionContext,
      formBinding: FormBinding
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
    final def bindFormDerivedFromState[T](
      form: State => Form[T],
      transition: T => Transition,
      outcomeFactory: OutcomeFactory,
      fallback: => Future[Result]
    )(implicit
      rc: RequestContext,
      request: Request[_],
      ec: ExecutionContext,
      formBinding: FormBinding
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
    final def parseJson[T: Reads](
      transition: T => Transition,
      outcomeFactory: OutcomeFactory,
      optionalIfFailure: Option[Result]
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
          optionalIfFailure
            .map(Future.successful)
            .getOrElse(
              Future.failed(new IllegalArgumentException)
            )
      }
    }
  }

  // ---------------------------------------
  //  ACTIONS DSL
  // ---------------------------------------

  /** Implicitly converts Executable to an action. */
  implicit protected def build(executable: internal.Executable)(implicit
    ec: ExecutionContext,
    formBinding: FormBinding
  ): Action[AnyContent] =
    action(implicit request => executable.execute(internal.Settings.default))

  /** Creates an action for a given async result function. */
  final def action(
    body: Request[_] => Future[Result]
  )(implicit ec: ExecutionContext): Action[AnyContent] =
    actionBuilder.async { implicit request =>
      implicit val rc: RequestContext = context(request)
      withValidRequest(body(request))
    }

  /**
    * Actions DSL: Simple action builder.
    */
  final object actions extends internal.SimpleActionsDSL {

    private[fsm] final override def wrap(
      body: Resolver => Future[Result]
    )(implicit
      request: Request[_],
      ec: ExecutionContext,
      formBinding: FormBinding
    ): Future[Result] =
      body(SimpleResolver)

    /**
      * Progress only if authorization succeeds.
      * @note use [[whenAuthorisedWithRetrievals]] if retrieved user data is needed
      */
    final def whenAuthorised(
      withAuthorised: WithAuthorised[_]
    ): internal.WhenAuthorised =
      new internal.WhenAuthorised(withAuthorised)

    /**
      * Progress only if authorization succeeds.
      * Pass supplied user data to the subsequent operations.
      * @tparam User authorised user information type
      * @note use [[whenAuthorised]] if retrievals are not needed
      */
    final def whenAuthorisedWithRetrievals[User](
      withAuthorised: WithAuthorised[User]
    ): internal.WhenAuthorisedWithRetrievals[User] =
      new internal.WhenAuthorisedWithRetrievals[User](withAuthorised)
  }

  /** Internal FSM controller models and functions. */
  final object internal {

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
      * Interface of the action body.
      */
    sealed trait Executable {

      /**
        * Execute and return the future of result.
        * @param outcomeFactoryOpt optional override of the outcomeFactory
        */
      def execute(
        settings: Settings
      )(implicit
        request: Request[_],
        ec: ExecutionContext,
        formBinding: FormBinding
      ): Future[Result]
    }

    /**
      * An [[Executable]] with common behaviour modifications functions:
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
      */
    sealed trait ExecutableWithDisplayOverrides extends ExecutableWithFinalTasks {

      /**
        * Change how the result is created.
        * Force displaying the state after action using [[renderState]].
        */
      final def display: ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings.setOutcomeFactory(JourneyController.this.helpers.display))
        }
      }

      /**
        * Change how the result is created.
        * Force displaying the state after action using custom renderer.
        */
      final def displayUsing(renderState: Renderer): ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(
              settings.setOutcomeFactory(JourneyController.this.helpers.displayUsing(renderState))
            )
        }
      }

      /**
        * Change how the result is created.
        * Force displaying the state after action using custom asynchronous renderer.
        */
      final def displayAsyncUsing(renderState: AsyncRenderer): ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(
              settings.setOutcomeFactory(
                JourneyController.this.helpers.displayAsyncUsing(renderState)
              )
            )
        }
      }

      /**
        * Change how the result is created.
        * Force redirect to the state after action using URL returned by [[getCallFor]].
        */
      final def redirect: ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings.setOutcomeFactory(JourneyController.this.helpers.redirect))
        }
      }

      /**
        * Change how the result is created.
        * If the state after action didn't change then display using [[renderState]],
        * otherwise redirect using URL returned by [[getCallFor]].
        */
      final def redirectOrDisplayIfSame: ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] = {
            implicit val rc: RequestContext = context(request)
            journeyService.currentState.flatMap { current =>
              outer.execute(
                settings.setOutcomeFactory(current.map {
                  case sb @ (state, breadcrumbs) =>
                    JourneyController.this.helpers.redirectOrDisplayIfSame(state)
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
      final def redirectOrDisplayUsingIfSame(renderState: Renderer): ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] = {
            implicit val rc: RequestContext = context(request)
            journeyService.currentState.flatMap { current =>
              outer.execute(
                settings.setOutcomeFactory(current.map {
                  case sb @ (state, breadcrumbs) =>
                    JourneyController.this.helpers.redirectOrDisplayUsingIfSame(state, renderState)
                })
              )
            }
          }
        }
      }

      /**
        * Change how the result is created.
        * If the state after action didn't change then display using custom asynchronous renderer,
        * otherwise redirect using URL returned by [[getCallFor]].
        */
      final def redirectOrDisplayAsyncUsingIfSame(
        renderState: AsyncRenderer
      ): ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] = {
            implicit val rc: RequestContext = context(request)
            journeyService.currentState.flatMap { current =>
              outer.execute(
                settings.setOutcomeFactory(current.map {
                  case sb @ (state, breadcrumbs) =>
                    JourneyController.this.helpers
                      .redirectOrDisplayAsyncUsingIfSame(state, renderState)
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
      final def redirectOrDisplayIf[S <: State: ClassTag]: ExecutableWithFinalTasks = {
        val outer                          = this
        val outcomeFactory: OutcomeFactory = JourneyController.this.helpers.redirectOrDisplayIf[S]
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings.setOutcomeFactory(outcomeFactory))
        }
      }

      /**
        * Change how the result is created.
        * If the state after action is of type S then display using custom renderer,
        * otherwise redirect using URL returned by [[getCallFor]].
        */
      final def redirectOrDisplayUsingIf[S <: State: ClassTag](
        renderState: Renderer
      ): ExecutableWithFinalTasks = {
        val outer = this
        val outcomeFactory: OutcomeFactory =
          JourneyController.this.helpers.redirectOrDisplayUsingIf[S](renderState)
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings.setOutcomeFactory(outcomeFactory))
        }
      }

      /**
        * Change how the result is created.
        * If the state after action is of type S then display using custom asynchronous renderer,
        * otherwise redirect using URL returned by [[getCallFor]].
        */
      final def redirectOrDisplayAsyncUsingIf[S <: State: ClassTag](
        renderState: AsyncRenderer
      ): ExecutableWithFinalTasks = {
        val outer = this
        val outcomeFactory: OutcomeFactory =
          JourneyController.this.helpers.redirectOrDisplayAsyncUsingIf[S](renderState)
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings.setOutcomeFactory(outcomeFactory))
        }
      }

      /**
        * Change how the result is created.
        * If the state after action is of type S then redirect using URL returned by [[getCallFor]]
        * otherwise display using [[renderState]],.
        */
      final def displayOrRedirectIf[S <: State: ClassTag]: ExecutableWithFinalTasks = {
        val outer                          = this
        val outcomeFactory: OutcomeFactory = JourneyController.this.helpers.displayOrRedirectIf[S]
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings.setOutcomeFactory(outcomeFactory))
        }
      }

      /**
        * Change how the result is created.
        * If the state after action is of type S then redirect using URL returned by [[getCallFor]],
        * otherwise display using custom renderer.
        */
      final def displayUsingOrRedirectIf[S <: State: ClassTag](
        renderState: Renderer
      ): ExecutableWithFinalTasks = {
        val outer = this
        val outcomeFactory: OutcomeFactory =
          JourneyController.this.helpers.displayUsingOrRedirectIf[S](renderState)
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings.setOutcomeFactory(outcomeFactory))
        }
      }

      /**
        * Change how the result is created.
        * If the state after action is of type S then redirect using URL returned by [[getCallFor]],
        * otherwise display using custom asynchronous renderer.
        */
      final def displayAsyncUsingOrRedirectIf[S <: State: ClassTag](
        renderState: AsyncRenderer
      ): ExecutableWithFinalTasks = {
        val outer = this
        val outcomeFactory: OutcomeFactory =
          JourneyController.this.helpers.displayAsyncUsingOrRedirectIf[S](renderState)
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings.setOutcomeFactory(outcomeFactory))
        }
      }
    }

    /**
      * An [[Executable]] with common functions:
      *
      * - [[andCleanBreadcrumbs]] to clean history (breadcrumbs)
      * - [[transform]] to transform final result
      * - [[recover]] to recover from an error using custom strategy
      * - [[recoverWith]] to recover from an error using custom strategy
      */
    sealed trait ExecutableWithFinalTasks extends Executable {

      /**
        * Run a task and continue if success.
        * Result of the task is forgotten.
        * If task fails and fallback [[optionalResultIfFailure]] is defined,
        * then return success with fallback result,
        * otherwise propagate the failure.
        *
        * @param task asynchronous task to run
        */
      def thenRunTask[T](
        task: Request[_] => Future[T],
        optionalResultIfFailure: Option[Result] = None
      ): ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer
              .execute(settings)
              .flatMap(result => task(request).map(_ => result))
              .recoverWith {
                case e =>
                  optionalResultIfFailure
                    .map(Future.successful)
                    .getOrElse(Future.failed(e))
              }
        }
      }

      /** Clean history (breadcrumbs) after an action. */
      final def andCleanBreadcrumbs(): ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings).flatMap { result =>
              // clears journey history
              journeyService
                .cleanBreadcrumbs(_ => Nil)(context(request), ec)
                .map(_ => result)
            }
        }
      }

      /** Transform result using provided partial function. */
      final def transform(fx: PartialFunction[Result, Result]): ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer
              .execute(settings)
              .map(result => fx.applyOrElse[Result, Result](result, _ => result))
        }
      }

      /** Recover from an error using custom strategy. */
      final def recover(
        recoveryStrategy: PartialFunction[Throwable, Result]
      ): ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings).recover(recoveryStrategy)
        }
      }

      /** Recover from an error using custom strategy. */
      final def recoverWith(
        recoveryStrategy: Request[_] => PartialFunction[Throwable, Future[Result]]
      ): ExecutableWithFinalTasks = {
        val outer = this
        new ExecutableWithFinalTasks {
          def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            outer.execute(settings).recoverWith(recoveryStrategy(request))
        }
      }
    }

    /**
      * Actions DSL: Action builder wrapping authorization function.
      * Does not supply any user data.
      * @note see also [[WhenAuthorisedWithRetrievals]]
      */
    final class WhenAuthorised(withAuthorised: WithAuthorised[_]) extends SimpleActionsDSL {

      private[fsm] final override def wrap(
        body: Resolver => Future[Result]
      )(implicit
        request: Request[_],
        ec: ExecutionContext,
        formBinding: FormBinding
      ): Future[Result] =
        withAuthorised(request) { _ =>
          body(SimpleResolver)
        }
    }

    /**
      * Actions DSL: Action builder wrapping authorization function supplying retrieved user data.
      * @note see also [[WhenAuthorised]]
      */
    final class WhenAuthorisedWithRetrievals[User](withAuthorised: WithAuthorised[User])
        extends SingleArgumentActionsDSL[User] {

      private[fsm] final override def wrap(
        body: Resolver => Future[Result]
      )(implicit
        request: Request[_],
        ec: ExecutionContext,
        formBinding: FormBinding
      ): Future[Result] =
        withAuthorised(request) { user =>
          body(new SingleArgumentResolver(user))
        }
    }

    /**
      * Actions DSL,
      * transition function types are left abstract.
      */
    protected sealed abstract class ActionsDSL {

      type Transition
      type TransitionWith[P]
      type TransitionWithRequest
      type TransitionWithRequestAnd[P]
      type GetAsyncFunction[R]
      type TupleWith[R]

      trait Resolver {
        def apply(f: Transition): model.Transition
        def apply[P](f: TransitionWith[P]): P => model.Transition
        def apply(f: TransitionWithRequest, r: Request[_]): model.Transition
        def apply[P](f: TransitionWithRequestAnd[P], r: Request[_]): P => model.Transition
        def apply[R](f: GetAsyncFunction[R], r: Request[_])(implicit
          ec: ExecutionContext
        ): Future[TupleWith[R]]
      }

      private[fsm] def wrap(
        body: Resolver => Future[Result]
      )(implicit
        request: Request[_],
        ec: ExecutionContext,
        formBinding: FormBinding
      ): Future[Result]

      /**
        * Displays the current state using default [[renderState]] function.
        * If there is no state yet then redirects to the start.
        * @note follow with [[renderUsing]] to change the default renderer.
        */
      final val showCurrentState: ExecutableWithDisplayOverrides = new ShowCurrentState()

      final class ShowCurrentState private[fsm] () extends ExecutableWithDisplayOverrides {
        override def execute(
          settings: Settings
        )(implicit
          request: Request[_],
          ec: ExecutionContext,
          formBinding: FormBinding
        ): Future[Result] =
          wrap { resolve =>
            implicit val rc: RequestContext = context(request)
            JourneyController.this.journeyService.currentState.flatMap {
              case Some(sb @ (state, breadcrumbs)) =>
                settings.outcomeFactoryWithDefault(JourneyController.this.helpers.display)(sb)(
                  request
                )

              case None =>
                JourneyController.this.helpers.redirectToStart
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
      final def show[S <: State: ClassTag]
        : Show[S] with WithRollback[S] with WithApply[S] with WithRedirect[S] =
        new Show[S](
          rollback = false,
          merger = None,
          fallback = JourneyController.this.helpers.defaultShowFallback
        ) with WithRollback[S] with WithApply[S] with WithRedirect[S]

      /** [[Show]] DSL data model. */
      sealed trait ShowLike[S <: State] {
        implicit val ct: ClassTag[S]
        val rollback: Boolean
        val merger: Option[Merger[S]]
        val fallback: Fallback

        @`inline` final val defaultOutcomeFactory: OutcomeFactory =
          JourneyController.this.helpers.redirectOrDisplayIf[S]
      }

      /** [[Show]] DSL wrapper. */
      sealed class Show[S <: State] private[fsm] (
        val rollback: Boolean,
        val merger: Option[Merger[S]],
        val fallback: Fallback
      )(implicit val ct: ClassTag[S])
          extends ShowLike[S]
          with ExecutableWithDisplayOverrides {

        override def execute(
          settings: Settings
        )(implicit
          request: Request[_],
          ec: ExecutionContext,
          formBinding: FormBinding
        ): Future[Result] =
          wrap { resolve =>
            implicit val rc: RequestContext = context(request)
            val outcomeFactory              = settings.outcomeFactoryWithDefault(defaultOutcomeFactory)
            merger match {
              case None =>
                JourneyController.this.helpers.showState[S](outcomeFactory, rollback, fallback)

              case Some(m) =>
                JourneyController.this.helpers
                  .showStateWithRollbackUsingMerger[S](m, outcomeFactory, fallback)
            }
          }
      }

      /** [[Show]] DSL modifications. */
      sealed trait WithRollback[S <: State] extends ShowLike[S] {

        /**
          * Modify [[show]] behaviour:
          * Try first rollback to the most recent state of type S
          * and display if found,
          * otherwise redirect back to the root state.
          *
          * @note to alter behaviour follow with [[orApply]] or [[orApplyWithRequest]]
          */
        @`inline` final def orRollback: Show[S] with WithApply[S] with WithRedirect[S] =
          new Show[S](rollback = true, merger = None, fallback = fallback)
            with WithApply[S]
            with WithRedirect[S]

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
        @`inline` final def orRollbackUsing(
          merger: Merger[S]
        ): Show[S] with WithApply[S] with WithRedirect[S] =
          new Show[S](rollback = true, merger = Some(merger), fallback = fallback)
            with WithApply[S]
            with WithRedirect[S]
      }

      /** [[Show]] DSL modifications. */
      sealed trait WithApply[S <: State] extends ShowLike[S] {

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
        @`inline` final def orApply(transition: Transition): OrApply = new OrApply(transition)

        final class OrApply private[fsm] (transition: Transition)
            extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              val outcomeFactory              = settings.outcomeFactoryWithDefault(defaultOutcomeFactory)
              merger match {
                case None =>
                  JourneyController.this.helpers
                    .showStateOrApply[S](resolve(transition), outcomeFactory, rollback)

                case Some(m) =>
                  JourneyController.this.helpers.showStateUsingMergerOrApply[S](m, outcomeFactory)(
                    resolve(transition)
                  )
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
        @`inline` final def orApplyWithRequest(
          transition: TransitionWithRequest
        ): OrApplyWithRequest =
          new OrApplyWithRequest(transition)

        final class OrApplyWithRequest private[fsm] (transition: TransitionWithRequest)
            extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              val outcomeFactory              = settings.outcomeFactoryWithDefault(defaultOutcomeFactory)
              merger match {
                case None =>
                  JourneyController.this.helpers
                    .showStateOrApply[S](resolve(transition, request), outcomeFactory, rollback)

                case Some(m) =>
                  JourneyController.this.helpers.showStateUsingMergerOrApply[S](m, outcomeFactory)(
                    resolve(transition, request)
                  )
              }
            }
        }
      }

      /** [[Show]] DSL modifications. */
      sealed trait WithRedirect[S <: State] extends ShowLike[S] {

        /**
          * Modify [[show]] behaviour:
          * Redirect to the current state if not S,
          * instead of using the default show fallback.
          */
        @`inline` final def orRedirectToCurrentState: Show[S] =
          new Show[S](
            rollback = rollback,
            merger = merger,
            fallback = JourneyController.this.helpers.redirectToCurrentStateFallback
          )

        /**
          * Modify [[show]] behaviour:
          * Redirect to the Start state if current state is not of S type,
          * instead of using the default show fallback.
          */
        @`inline` final def orRedirectToStart: Show[S] =
          new Show[S](
            rollback = rollback,
            merger = merger,
            fallback = JourneyController.this.helpers.redirectToStartFallback
          )

        /**
          * Modify [[show]] behaviour:
          * Return the given result if current state is not of S type,
          * instead of using the default show fallback.
          */
        @`inline` final def orReturn(result: => Result): Show[S] =
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
        @`inline` final def orRedirectTo(call: Call): Show[S] =
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
      @`inline` final def apply(transition: Transition): Apply = new Apply(transition)

      final class Apply private[fsm] (transition: Transition)
          extends ExecutableWithDisplayOverrides {
        override def execute(
          settings: Settings
        )(implicit
          request: Request[_],
          ec: ExecutionContext,
          formBinding: FormBinding
        ): Future[Result] =
          wrap { resolve =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.helpers
              .apply(
                resolve(transition),
                settings.outcomeFactoryOpt.getOrElse(JourneyController.this.helpers.redirect)
              )
          }
      }

      /** Apply state transition and redirect to the new state. */
      @`inline` final def applyWithRequest(transition: TransitionWithRequest): ApplyWithRequest =
        new ApplyWithRequest(transition)

      final class ApplyWithRequest private[fsm] (transition: TransitionWithRequest)
          extends ExecutableWithDisplayOverrides {
        override def execute(
          settings: Settings
        )(implicit
          request: Request[_],
          ec: ExecutionContext,
          formBinding: FormBinding
        ): Future[Result] =
          wrap { resolve =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.helpers
              .apply(
                resolve(transition, request),
                settings.outcomeFactoryOpt.getOrElse(JourneyController.this.helpers.redirect)
              )
          }
      }

      /**
        * Bind the form to the request.
        * If valid, apply the following transition,
        * if not valid, redirect back to the current state with failed form.
        * @tparam Payload form output type
        */
      @`inline` final def bindForm[Payload](form: Form[Payload]): BindForm[Payload] =
        new BindForm[Payload](form)

      final class BindForm[Payload] private[fsm] (form: Form[Payload]) {

        /**
          * Apply the state transition parametrized by the form output
          * and redirect to the URL matching the new state.
          */
        @`inline` final def apply(transition: TransitionWith[Payload]): Apply =
          new Apply(transition)

        final class Apply private[fsm] (transition: TransitionWith[Payload])
            extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.helpers.bindForm(
                form,
                resolve(transition),
                settings.outcomeFactoryWithDefault(JourneyController.this.helpers.redirect),
                JourneyController.this.helpers.redirectToStart
              )
            }
        }

        /**
          * Apply the state transition parametrized by the form output
          * and redirect to the URL matching the new state.
          */
        @`inline` final def applyWithRequest(
          transition: TransitionWithRequestAnd[Payload]
        ): ApplyWithRequest =
          new ApplyWithRequest(transition)

        final class ApplyWithRequest private[fsm] (
          transition: TransitionWithRequestAnd[Payload]
        ) extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.helpers.bindForm(
                form,
                resolve(transition, request),
                settings.outcomeFactoryWithDefault(JourneyController.this.helpers.redirect),
                JourneyController.this.helpers.redirectToStart
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
      @`inline` final def bindFormDerivedFromState[Payload](
        form: State => Form[Payload]
      ): BindFormDerivedFromState[Payload] =
        new BindFormDerivedFromState[Payload](form)

      final class BindFormDerivedFromState[Payload] private[fsm] (form: State => Form[Payload]) {

        /**
          * Apply the state transition parametrized by the form output
          * and redirect to the URL matching the new state.
          */
        @`inline` final def apply(transition: TransitionWith[Payload]): Apply =
          new Apply(transition)

        final class Apply private[fsm] (transition: TransitionWith[Payload])
            extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.helpers.bindFormDerivedFromState(
                form,
                resolve(transition),
                settings.outcomeFactoryWithDefault(JourneyController.this.helpers.redirect),
                JourneyController.this.helpers.redirectToStart
              )
            }
        }

        /**
          * Apply the state transition parametrized by the form output
          * and redirect to the URL matching the new state.
          */
        @`inline` final def applyWithRequest(
          transition: TransitionWithRequestAnd[Payload]
        ): ApplyWithRequest =
          new ApplyWithRequest(transition)

        final class ApplyWithRequest private[fsm] (
          transition: TransitionWithRequestAnd[Payload]
        ) extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.helpers.bindFormDerivedFromState(
                form,
                resolve(transition, request),
                settings.outcomeFactoryWithDefault(JourneyController.this.helpers.redirect),
                JourneyController.this.helpers.redirectToStart
              )
            }
        }
      }

      /**
        * Parse the JSON body of the request.
        * If valid, apply the following transition,
        * if not valid, throw an exception.
        * @tparam Entity entity
        */
      @`inline` final def parseJson[Entity: Reads]: ParseJson[Entity] =
        new ParseJson[Entity](None)

      /**
        * Parse the JSON body of the request.
        * If valid, apply the following transition,
        * if not valid, return the alternative result.
        * @tparam Entity entity
        */
      @`inline` final def parseJsonWithFallback[Entity: Reads](
        ifFailure: Result
      ): ParseJson[Entity] =
        new ParseJson[Entity](Some(ifFailure))

      final class ParseJson[Entity: Reads] private[fsm] (
        optionalIfFailure: Option[Result]
      ) {

        /**
          * Parse request's body as JSON and apply the state transition if success,
          * otherwise return ifFailure result.
          */
        @`inline` final def apply(transition: TransitionWith[Entity]): Apply = new Apply(transition)

        final class Apply private[fsm] (transition: TransitionWith[Entity])
            extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.helpers.parseJson(
                resolve(transition),
                settings.outcomeFactoryWithDefault(JourneyController.this.helpers.redirect),
                optionalIfFailure
              )
            }
        }

        /**
          * Parse request's body as JSON and apply the state transition if success,
          * otherwise return ifFailure result.
          */
        @`inline` final def applyWithRequest(
          transition: TransitionWithRequestAnd[Entity]
        ): ApplyWithRequest =
          new ApplyWithRequest(transition)

        final class ApplyWithRequest private[fsm] (
          transition: TransitionWithRequestAnd[Entity]
        ) extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.helpers.parseJson(
                resolve(transition, request),
                settings.outcomeFactoryWithDefault(JourneyController.this.helpers.redirect),
                optionalIfFailure
              )
            }
        }
      }

      /**
        * Wait until the state becomes of S type and display it using default [[renderState]],
        * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
        */
      @`inline` final def waitForStateAndDisplay[S <: State: ClassTag](
        timeoutInSeconds: Int
      )(implicit scheduler: akka.actor.Scheduler): WaitFor[S] =
        new WaitFor[S](timeoutInSeconds)(JourneyController.this.helpers.display)

      /**
        * Wait until the state becomes of S type and display it using custom renderer,
        * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
        */
      @`inline` final def waitForStateAndDisplayUsing[S <: State: ClassTag](
        timeoutInSeconds: Int,
        renderer: Renderer
      )(implicit scheduler: akka.actor.Scheduler): WaitFor[S] =
        new WaitFor[S](timeoutInSeconds)(JourneyController.this.helpers.displayUsing(renderer))

      /**
        * Wait until the state becomes of S type and display it using custom renderer,
        * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
        */
      @`inline` final def waitForStateAndDisplayAsyncUsing[S <: State: ClassTag](
        timeoutInSeconds: Int,
        renderer: AsyncRenderer
      )(implicit scheduler: akka.actor.Scheduler): WaitFor[S] =
        new WaitFor[S](timeoutInSeconds)(JourneyController.this.helpers.displayAsyncUsing(renderer))

      /**
        * Wait until the state becomes of S type and redirect to it,
        * or if timeout expires raise a [[java.util.concurrent.TimeoutException]].
        */
      @`inline` final def waitForStateThenRedirect[S <: State: ClassTag](
        timeoutInSeconds: Int
      )(implicit scheduler: akka.actor.Scheduler): WaitFor[S] =
        new WaitFor[S](timeoutInSeconds)(JourneyController.this.helpers.redirect)

      final class WaitFor[S <: State: ClassTag] private[fsm] (timeoutInSeconds: Int)(
        outcomeFactory: OutcomeFactory
      )(implicit scheduler: akka.actor.Scheduler)
          extends ExecutableWithDisplayOverrides {
        override def execute(
          settings: Settings
        )(implicit
          request: Request[_],
          ec: ExecutionContext,
          formBinding: FormBinding
        ): Future[Result] =
          wrap { resolve =>
            waitForUsing(
              _ => Future.failed(new TimeoutException),
              outcomeFactory
            )
          }

        private def waitForUsing(
          ifTimeout: Request[_] => Future[Result],
          outcomeFactory: OutcomeFactory
        )(implicit
          request: Request[_],
          ec: ExecutionContext
        ): Future[Result] = {
          implicit val rc: RequestContext =
            JourneyController.this.context(request)

          val timeoutNanoTime: Long =
            System.nanoTime() + timeoutInSeconds * 1000000000L

          JourneyController.this.helpers.waitFor[S](500, timeoutNanoTime)(outcomeFactory)(ifTimeout)
        }

        /**
          * Wait until the state becomes of S type,
          * or when timeout expires apply the transition and redirect to the new state.
          *
          * @note follow with [[display]] or [[redirectOrDisplayIf]] to display instead of redirecting.
          */
        @`inline` final def orApplyOnTimeout(transition: Transition): OrApply =
          new OrApply(transition)

        final class OrApply private[fsm] (transition: Transition)
            extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              waitForUsing(
                { implicit request =>
                  implicit val rc: RequestContext = JourneyController.this.context(request)
                  JourneyController.this.helpers.apply(
                    resolve(transition),
                    settings.outcomeFactoryOpt.getOrElse(JourneyController.this.helpers.redirect)
                  )
                },
                outcomeFactory
              )
            }
        }

        /**
          * Wait until the state becomes of S type,
          * or when timeout expires apply the transition and redirect to the new state.
          *
          * @note follow with [[display]] or [[redirectOrDisplayIf]] to display instead of redirecting.
          */
        @`inline` final def orApplyWithRequestOnTimeout(
          transition: TransitionWithRequest
        ): OrApplyWithRequest =
          new OrApplyWithRequest(transition)

        final class OrApplyWithRequest private[fsm] (transition: TransitionWithRequest)
            extends ExecutableWithDisplayOverrides {
          override def execute(
            settings: Settings
          )(implicit
            request: Request[_],
            ec: ExecutionContext,
            formBinding: FormBinding
          ): Future[Result] =
            wrap { resolve =>
              waitForUsing(
                { implicit request =>
                  implicit val rc: RequestContext = JourneyController.this.context(request)
                  JourneyController.this.helpers.apply(
                    resolve(transition, request),
                    settings.outcomeFactoryOpt.getOrElse(JourneyController.this.helpers.redirect)
                  )
                },
                outcomeFactory
              )
            }
        }
      }
    }

    /** Simple Actions DSL with no arguments for transitions. */
    sealed abstract class SimpleActionsDSL extends ActionsDSL {

      final type Transition                  = model.Transition
      final type TransitionWith[P]           = P => model.Transition
      final type TransitionWithRequest       = Request[_] => model.Transition
      final type TransitionWithRequestAnd[P] = Request[_] => P => model.Transition
      final type GetAsyncFunction[R]         = Request[_] => Future[R]
      final type TupleWith[R]                = R

      final object SimpleResolver extends Resolver {
        override def apply(f: Transition): model.Transition                           = f
        override def apply[P](f: TransitionWith[P]): P => model.Transition            = f
        override def apply(f: TransitionWithRequest, r: Request[_]): model.Transition = f(r)
        override def apply[P](
          f: TransitionWithRequestAnd[P],
          r: Request[_]
        ): P => model.Transition =
          f(r)
        override def apply[R](f: GetAsyncFunction[R], request: Request[_])(implicit
          ec: ExecutionContext
        ): Future[TupleWith[R]] = f(request)
      }

      /**
        * Call async function and, if successful, propagate returned value
        * as a next input argument to subsequent transitions.
        */
      final def getAsync[R](get: GetAsyncFunction[R]): GetAsync0[R] =
        new GetAsync0[R](get)
    }

    /** Actions DSL with single argument for transitions. */
    protected sealed abstract class SingleArgumentActionsDSL[A] extends ActionsDSL {

      final type Transition                  = A => model.Transition
      final type TransitionWith[P]           = A => P => model.Transition
      final type TransitionWithRequest       = Request[_] => A => model.Transition
      final type TransitionWithRequestAnd[P] = Request[_] => A => P => model.Transition
      final type GetAsyncFunction[R]         = Request[_] => A => Future[R]
      final type TupleWith[R]                = (A, R)

      final class SingleArgumentResolver(a: A) extends Resolver {
        override def apply(f: Transition): model.Transition                           = f(a)
        override def apply[P](f: TransitionWith[P]): P => model.Transition            = f(a)
        override def apply(f: TransitionWithRequest, r: Request[_]): model.Transition = f(r)(a)
        override def apply[P](
          f: TransitionWithRequestAnd[P],
          r: Request[_]
        ): P => model.Transition =
          f(r)(a)
        override def apply[R](f: GetAsyncFunction[R], request: Request[_])(implicit
          ec: ExecutionContext
        ): Future[TupleWith[R]] = f(request)(a).map(r => (a, r))
      }

      /**
        * Call async function and, if successful, propagate returned value
        * as a next input argument to subsequent transitions.
        */
      final def getAsync[R](get: GetAsyncFunction[R]): GetAsync1[A, R] =
        new GetAsync1[A, R](get, this)
    }

    /** Actions DSL with double arguments for transitions. */
    protected sealed abstract class DoubleArgumentActionsDSL[A, B](
      outer: SingleArgumentActionsDSL[A]
    ) extends ActionsDSL {

      final type Transition                  = A => B => model.Transition
      final type TransitionWith[P]           = A => B => P => model.Transition
      final type TransitionWithRequest       = Request[_] => A => B => model.Transition
      final type TransitionWithRequestAnd[P] = Request[_] => A => B => P => model.Transition
      final type GetAsyncFunction[R]         = Request[_] => A => B => Future[R]
      final type TupleWith[R]                = (A, B, R)

      final class DoubleArgumentResolver(a: A, b: B) extends Resolver {
        override def apply(f: Transition): model.Transition                           = f(a)(b)
        override def apply[P](f: TransitionWith[P]): P => model.Transition            = f(a)(b)
        override def apply(f: TransitionWithRequest, r: Request[_]): model.Transition = f(r)(a)(b)
        override def apply[P](
          f: TransitionWithRequestAnd[P],
          r: Request[_]
        ): P => model.Transition =
          f(r)(a)(b)
        override def apply[R](f: GetAsyncFunction[R], request: Request[_])(implicit
          ec: ExecutionContext
        ): Future[TupleWith[R]] = f(request)(a)(b).map(r => (a, b, r))
      }
    }

    /**
      * Actions DSL: Action builder wrapping async function returning some value.
      */
    final class GetAsync0[R](get: Request[_] => Future[R]) extends SingleArgumentActionsDSL[R] {

      final def wrap(
        body: Resolver => Future[Result]
      )(implicit
        request: Request[_],
        ec: ExecutionContext,
        formBinding: FormBinding
      ): Future[Result] =
        get(request).flatMap { arg =>
          body(new SingleArgumentResolver(arg))
        }
    }

    /**
      * Actions DSL: Action builder wrapping async function returning some value.
      */
    final class GetAsync1[A, T](
      get: Request[_] => A => Future[T],
      outer: SingleArgumentActionsDSL[A]
    ) extends DoubleArgumentActionsDSL[A, T](outer) {

      final def wrap(
        body: Resolver => Future[Result]
      )(implicit
        request: Request[_],
        ec: ExecutionContext,
        formBinding: FormBinding
      ): Future[Result] =
        outer.wrap { resolverA =>
          resolverA(get, request).flatMap {
            case (a, b) =>
              body(new DoubleArgumentResolver(a, b))
          }
        }
    }
  }

  /** Legacy and deprecated stuff replaced by Actions DSL. */
  final object legacy {

    type ExpectedStates = PartialFunction[State, Unit]

    /** Check if the expected state exists in the journey history (breadcrumbs). */
    @tailrec
    final def hasState(
      expectedStates: ExpectedStates,
      stateAndBreadcrumbs: StateAndBreadcrumbs
    ): Boolean =
      stateAndBreadcrumbs match {
        case (state, breadcrumbs) =>
          if (expectedStates.isDefinedAt(state)) true
          else
            breadcrumbs match {
              case Nil    => false
              case s :: b => hasState(expectedStates, (s, b))
            }
      }

    /**
      * Display the state requested by the type parameter S.
      * If the current state is not of type S,
      * try rollback to the most recent state matching S,
      * or redirect back to the root state.
      * @tparam S type of the state to display
      */
    @deprecated("Prefer helpers.showState[S]", "0.75.0")
    final def showState(
      expectedStates: ExpectedStates
    )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
      for {
        sbopt <- journeyService.currentState
        result <- sbopt match {
                    case None => helpers.redirectToStart
                    case Some(stateAndBreadcrumbs) =>
                      if (hasState(expectedStates, stateAndBreadcrumbs))
                        journeyService.currentState
                          .flatMap(rollbackTo(expectedStates)(helpers.redirectToStart))
                      else helpers.redirectToStart
                  }
      } yield result

    /** Rollback journey state and history (breadcrumbs) back to the most recent state matching expectation. */
    @deprecated("Prefer helpers.rollbackTo[S]", "0.75.0")
    final def rollbackTo(expectedState: PartialFunction[State, Unit])(
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

    /** Apply transition parametrized by an authorised user data. */
    @deprecated("Prefer DSL actions.whenAuthorised[User].apply(transition)", "0.72.0")
    final def whenAuthorised[User](
      withAuthorised: WithAuthorised[User]
    )(transition: User => Transition)(
      outcomeFactory: OutcomeFactory
    )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
      withAuthorised(request) { user: User =>
        helpers.apply(transition(user), outcomeFactory)
      }

    /** Apply transition parametrized by an authorised user data and form output. */
    @deprecated(
      "Prefer DSL actions.whenAuthorised[User].bindForm(form).apply(transition)",
      "0.72.0"
    )
    final def whenAuthorisedWithForm[User, Payload](
      withAuthorised: WithAuthorised[User]
    )(form: Form[Payload])(
      transition: User => Payload => Transition
    )(implicit
      rc: RequestContext,
      request: Request[_],
      ec: ExecutionContext,
      formBinding: FormBinding
    ): Future[Result] =
      withAuthorised(request) { user: User =>
        helpers.bindForm(form, transition(user))
      }

    /** Apply two transitions in order:
      * - first: simple transition
      * - second: transition parametrized by an authorised user data and form output
      */
    final def whenAuthorisedWithBootstrapAndForm[User, Payload, T](
      bootstrap: Transition
    )(withAuthorised: WithAuthorised[User])(form: Form[Payload])(
      transition: User => Payload => Transition
    )(implicit
      rc: RequestContext,
      request: Request[_],
      ec: ExecutionContext,
      formBinding: FormBinding
    ): Future[Result] =
      withAuthorised(request) { user: User =>
        journeyService
          .apply(bootstrap)
          .flatMap(_ => helpers.bindForm(form, transition(user)))
      }

    /** When user is authorized then render current state or rewind to the previous state matching expectation. */
    @deprecated("Prefer DSL actions.whenAuthorised[User].show[ExpectedState]", "0.72.0")
    final def showStateWhenAuthorised[User](withAuthorised: WithAuthorised[User])(
      expectedStates: ExpectedStates
    )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
      withAuthorised(request) { _ =>
        showState(expectedStates)
      }

    @deprecated("Prefer DSL actions.show[ExpectedState]", "0.71.0")
    final def actionShowState(
      expectedStates: ExpectedStates
    )(implicit ec: ExecutionContext): Action[AnyContent] =
      action { implicit request =>
        implicit val rc: RequestContext = context(request)
        showState(expectedStates)
      }

    @deprecated("Prefer DSL actions.whenAuthorised(withAuthorised).show[ExpectedState]", "0.72.0")
    final def actionShowStateWhenAuthorised[User](
      withAuthorised: WithAuthorised[User]
    )(expectedStates: ExpectedStates)(implicit ec: ExecutionContext): Action[AnyContent] =
      action { implicit request =>
        implicit val rc: RequestContext = context(request)
        showStateWhenAuthorised(withAuthorised)(expectedStates)
      }
  }
}

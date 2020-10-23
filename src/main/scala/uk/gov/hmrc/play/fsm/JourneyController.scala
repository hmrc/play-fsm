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

  /** Displays template for the state and breadcrumbs, eventually override to change details */
  val display: RouteFactory = (state: StateAndBreadcrumbs) =>
    (request: Request[_]) => renderState(state._1, state._2, None)(request)

  /** Redirects to the endpoint matching state, eventually override to change details */
  val redirect: RouteFactory =
    (state: StateAndBreadcrumbs) =>
      (request: Request[_]) => Results.Redirect(getCallFor(state._1)(request))

  /** Returns a call to the previous state */
  protected def backLinkFor(breadcrumbs: Breadcrumbs)(implicit request: Request[_]): Call =
    breadcrumbs.headOption.map(getCallFor).getOrElse(getCallFor(journeyService.model.root))

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

  /**
    * Apply state transition and, depending on the outcome,
    * display if the new state is the same as previous,
    * or redirect to the URL matching the new state.
    */
  protected final def applyThenRedirectOrDisplay(transition: Transition)(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    journeyService.currentState
      .flatMap {
        case None =>
          journeyService
            .apply(transition)
            .map(redirect)
            .map(_(request))
            .recover {
              case TransitionNotAllowed(origin, breadcrumbs, _) =>
                redirect((origin, breadcrumbs))(request) // renders current state back
            }

        case Some((state, breadcrumbs)) =>
          journeyService
            .apply(transition)
            .map {
              case sb @ (newState, newBreadcrumbs) =>
                if (newState == state) display(sb)
                else redirect(sb)
            }
            .map(_(request))
            .recover {
              case TransitionNotAllowed(origin, breadcrumbs, _) =>
                display((origin, breadcrumbs))(request) // renders current state back
            }
      }

  /**
    * Apply state transition and, depending on the outcome,
    * display if the new state matches expectations,
    * or redirect to the URL matching the new state.
    */
  protected final def applyAndMatch(transition: Transition)(
    expectedStates: ExpectedStates
  )(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    journeyService
      .apply(transition)
      .map {
        case sb @ (state, breadcrumbs) =>
          if (expectedStates.isDefinedAt(state)) display(sb)
          else redirect(sb)
      }
      .map(_(request))
      .recover {
        case TransitionNotAllowed(origin, breadcrumbs, _) =>
          // renders current state back
          if (expectedStates.isDefinedAt(origin))
            display((origin, breadcrumbs))(request)
          else
            redirect((origin, breadcrumbs))(request)
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
    * try to rewind the history back to the nearest state matching S,
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
    * If the current state is not of type S
    * try to rewind the history back to the nearest state matching S
    * and apply merge function to reconcile the new state with the previous state,
    * or redirect back to the root state.
    */
  protected final def showStateUsingMerge[S <: State: ClassTag](
    expectedStates: ExpectedStates
  )(
    merger: Merger[S]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      stateAndBreadcrumbsOpt <- journeyService.currentState
      result <- stateAndBreadcrumbsOpt match {
                  case None => redirectToStart
                  case sb @ Some((state, breadcrumbs)) =>
                    if (hasState(expectedStates, (state, breadcrumbs)))
                      rewindAndModify[S](expectedStates)(merger.withState(state))(redirectToStart)(
                        sb
                      )
                    else redirectToStart
                }
    } yield result

  /**
    * Display the journey state requested by the type parameter S.
    * If the current state is not of type S,
    * try to rewind the history back to the nearest state matching S,
    * If there exists no matching state S in the history,
    * apply the transition and redirect to the new state.
    * If transition is not allowed then redirect back to the current state.
    * @tparam S type of the state to display
    */
  protected final def showStateOrApply(
    expectedStates: ExpectedStates
  )(
    transition: Transition
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      stateAndBreadcrumbsOpt <- journeyService.currentState
      result <- stateAndBreadcrumbsOpt match {
                  case None => applyAndMatch(transition)(expectedStates)
                  case sb @ Some(stateAndBreadcrumbs) =>
                    if (hasState(expectedStates, stateAndBreadcrumbs))
                      rewindTo(expectedStates)(applyAndMatch(transition)(expectedStates))(sb)
                    else applyAndMatch(transition)(expectedStates)
                }
    } yield result

  /**
    * Display the journey state requested by the type parameter S.
    * If the current state is not of type S,
    * try to rewind the history back to the nearest state matching S
    * and apply merge function to reconcile the new state with the current state.
    * If there exists no matching state S in the history,
    * apply the transition and redirect to the new state.
    * If transition is not allowed then redirect back to the current state.
    * @tparam S type of the state to display
    */
  protected final def showStateUsingMergeOrApply[S <: State: ClassTag](
    expectedStates: ExpectedStates
  )(
    merger: Merger[S]
  )(
    transition: Transition
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      stateAndBreadcrumbsOpt <- journeyService.currentState
      result <- stateAndBreadcrumbsOpt match {
                  case None => applyAndMatch(transition)(expectedStates)
                  case sb @ Some((state, breadcrumbs)) =>
                    if (hasState(expectedStates, (state, breadcrumbs)))
                      rewindAndModify[S](expectedStates)(merger.withState(state))(
                        applyAndMatch(transition)(expectedStates)
                      )(
                        sb
                      )
                    else applyAndMatch(transition)(expectedStates)
                }
    } yield result

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

  /** Rewind journey state and history (breadcrumbs) back to the nearest state matching expectations. */
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

  /**
    * Rewind journey state and history (breadcrumbs) back to the nearest state matching expectations,
    * and if exists, apply modification.
    */
  private final def rewindAndModify[S <: State: ClassTag](
    expectedState: PartialFunction[State, Unit]
  )(modification: S => S)(
    fallback: => Future[Result]
  )(
    stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    stateAndBreadcrumbsOpt match {
      case None => fallback
      case Some((state, breadcrumbs)) =>
        if (expectedState.isDefinedAt(state))
          journeyService
            .modify(modification)
            .map {
              case (s, b) if implicitly[ClassTag[S]].runtimeClass.isAssignableFrom(s.getClass) =>
                display((s, b))
              case other => redirect(other)
            }
            .map(_(request))
        else journeyService.stepBack.flatMap(rewindAndModify(expectedState)(modification)(fallback))
    }

  //-------------------------------------------------
  // FORM BINDING HELPERS
  //-------------------------------------------------

  /**
    * Bind form and apply transition if success,
    * otherwise redirect to the current state with form errors in the flash scope.
    */
  protected final def bindForm[T](form: Form[T], transition: T => Transition)(implicit
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
        userInput => apply(transition(userInput), redirect)
      )

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

  protected final def showStateWhenAuthorisedUsingMerge[User, S <: State: ClassTag](
    withAuthorised: WithAuthorised[User]
  )(
    expectedStates: ExpectedStates
  )(
    merge: Merger[S]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { _ =>
      showStateUsingMerge(expectedStates)(merge)
    }

  // ---------------------------------------
  //  ACTIONS DSL
  // ---------------------------------------

  /** Creates an action for a given async result function. */
  protected final def action(
    body: Request[_] => Future[Result]
  )(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val rc: RequestContext = context(request)
      withValidRequest(body(request))
    }

  /** Interface of Action building components */
  sealed trait Executable {

    def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result]

    /** Clean history (breadcrumbs) afterwards. */
    def andCleanBreadcrumbs(): Executable = {
      val outer = this
      new Executable {
        def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          outer.execute.flatMap { result =>
            // clears journey history
            journeyService
              .cleanBreadcrumbs(_ => Nil)(context(request), ec)
              .map(_ => result)
          }
      }
    }
  }

  implicit protected def build(executable: Executable)(implicit
    ec: ExecutionContext
  ): Action[AnyContent] =
    action(implicit request => executable.execute)

  /** Collection of DSL-style action builders */
  object actions {

    /**
      * Display the state requested by the type parameter S.
      * If the current state is not of type S,
      * try to rewind the history back to the nearest state matching S,
      * or redirect back to the root state.
      * @tparam S type of the state to display
      */
    def show[S <: State: ClassTag]: Show[S] = new Show[S]

    class Show[S <: State: ClassTag] private[actions] () extends Executable {
      override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = context(request)
        JourneyController.this.showState { case _: S => }
      }

      /**
        * Display the journey state requested by the type parameter S.
        * If the current state is not of type S,
        * try to rewind the history back to the nearest state matching S,
        * If there exists no matching state S in the history,
        * apply the transition and redirect to the new state.
        * If transition is not allowed then redirect back to the current state.
        * @tparam S type of the state to display
        */
      def orApply(transition: Transition): OrApply = new OrApply(transition)

      class OrApply private[actions] (transition: Transition) extends Executable {
        override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.showStateOrApply { case _: S => }(transition)
        }
      }

      /**
        * Display the journey state requested by the type parameter S.
        * If the current state is not of type S,
        * try to rewind the history back to the nearest state matching S,
        * If there exists no matching state S in the history,
        * apply the transition and redirect to the new state.
        * If transition is not allowed then redirect back to the current state.
        * @tparam S type of the state to display
        */
      def orApplyWithRequest(transition: Request[_] => Transition): OrApplyWithRequest =
        new OrApplyWithRequest(transition)

      class OrApplyWithRequest private[actions] (transition: Request[_] => Transition)
          extends Executable {
        override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.showStateOrApply { case _: S => }(transition(request))
        }
      }

      /**
        * Display the state requested by the type parameter S.
        * If the current state is not of type S
        * try to rewind the history back to the nearest state matching S
        * and apply merge function to reconcile the new state with the previous state,
        * or redirect back to the root state.
        */
      def using(merger: Merger[S]): UsingMerger = new UsingMerger(merger)

      class UsingMerger private[actions] (merger: Merger[S]) extends Executable {
        override def execute(implicit
          request: Request[_],
          ec: ExecutionContext
        ): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.showStateUsingMerge[S] { case _: S => }(merger)
        }

        /**
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try to rewind the history back to the nearest state matching S
          * and apply merge function to reconcile the new state with the current state.
          * If there exists no matching state S in the history,
          * apply the transition and redirect to the new state.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        def orApply(transition: Transition): OrApply = new OrApply(transition)

        class OrApply private[actions] (transition: Transition) extends Executable {
          override def execute(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.showStateUsingMergeOrApply { case _: S => }(merger)(
              transition
            )
          }
        }

        /**
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try to rewind the history back to the nearest state matching S
          * and apply merge function to reconcile the new state with the current state.
          * If there exists no matching state S in the history,
          * apply the transition and redirect to the new state.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        def orApplyWithRequest(transition: Request[_] => Transition): OrApplyWithRequest =
          new OrApplyWithRequest(transition)

        class OrApplyWithRequest private[actions] (transition: Request[_] => Transition)
            extends Executable {
          override def execute(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.showStateUsingMergeOrApply { case _: S => }(merger)(
              transition(request)
            )
          }
        }
      }
    }

    /** Apply state transition and redirect to the URL matching the new state. */
    def apply(transition: Transition): Apply = new Apply(transition)

    class Apply private[actions] (transition: Transition) extends Executable {
      override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = JourneyController.this.context(request)
        JourneyController.this.apply(transition, JourneyController.this.redirect)
      }
    }

    /**
      * Apply state transition and, depending on the outcome,
      * display if the new state is the same as previous,
      * or redirect to the URL matching the new state.
      */
    def applyThenRedirectOrDisplay(
      transition: Request[_] => Transition
    ): ApplyThenRedirectOrDisplay =
      new ApplyThenRedirectOrDisplay(transition)

    class ApplyThenRedirectOrDisplay private[actions] (transition: Request[_] => Transition)
        extends Executable {
      override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = JourneyController.this.context(request)
        JourneyController.this
          .applyThenRedirectOrDisplay(transition(request))
      }
    }

    /** Apply state transition and redirect to the URL matching the new state. */
    def applyWithRequest(transition: Request[_] => Transition): ApplyWithRequest =
      new ApplyWithRequest(transition)

    class ApplyWithRequest private[actions] (transition: Request[_] => Transition)
        extends Executable {
      override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
        implicit val rc: RequestContext = JourneyController.this.context(request)
        JourneyController.this.apply(transition(request), JourneyController.this.redirect)
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

    class BindForm[Payload] private[actions] (form: Form[Payload]) {

      /**
        * Apply state transition parametrized by the form output
        * and redirect to the URL matching the new state.
        */
      def apply(transition: Payload => Transition): Apply = new Apply(transition)

      class Apply private[actions] (transition: Payload => Transition) extends Executable {
        override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.bindForm(form, transition)
        }
      }

      /**
        * Apply state transition parametrized by the form output
        * and redirect to the URL matching the new state.
        */
      def applyWithRequest(transition: Request[_] => Payload => Transition): ApplyWithRequest =
        new ApplyWithRequest(transition)

      class ApplyWithRequest private[actions] (transition: Request[_] => Payload => Transition)
          extends Executable {
        override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.bindForm(form, transition(request))
        }
      }
    }

    /**
      * Progress only if authorization succeeds.
      * Use returned value in the following operations.
      * @tparam User authorised user information type
      */
    def whenAuthorised[User](withAuthorised: WithAuthorised[User]): WhenAuthorised[User] =
      new WhenAuthorised[User](withAuthorised)

    class WhenAuthorised[User] private[actions] (withAuthorised: WithAuthorised[User]) {

      /**
        * Display the state requested by the type parameter S.
        * If the current state is not of type S,
        * try to rewind the history back to the nearest state matching S,
        * or redirect back to the root state.
        * @tparam S type of the state to display
        */
      def show[S <: State: ClassTag]: Show[S] = new Show[S]

      class Show[S <: State: ClassTag] private[actions] () extends Executable {
        override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
          implicit val rc: RequestContext = JourneyController.this.context(request)
          JourneyController.this.showStateWhenAuthorised(withAuthorised) { case _: S => }
        }

        /**
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try to rewind the history back to the nearest state matching S,
          * If there exists no matching state S in the history,
          * apply the transition and redirect to the new state.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        def orApply(transition: User => Transition): OrApply = new OrApply(transition)

        class OrApply private[actions] (transition: User => Transition) extends Executable {
          override def execute(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            withAuthorised(request) { user: User =>
              JourneyController.this.showStateOrApply { case _: S => }(transition(user))
            }
          }
        }

        /**
          * Display the journey state requested by the type parameter S.
          * If the current state is not of type S,
          * try to rewind the history back to the nearest state matching S,
          * If there exists no matching state S in the history,
          * apply the transition and redirect to the new state.
          * If transition is not allowed then redirect back to the current state.
          * @tparam S type of the state to display
          */
        def orApplyWithRequest(transition: Request[_] => User => Transition): OrApplyWithRequest =
          new OrApplyWithRequest(transition)

        class OrApplyWithRequest private[actions] (transition: Request[_] => User => Transition)
            extends Executable {
          override def execute(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            withAuthorised(request) { user: User =>
              JourneyController.this.showStateOrApply { case _: S => }(transition(request)(user))
            }
          }
        }

        /**
          * Display the state requested by the type parameter S.
          * If the current state is not of type S,
          * then try to rewind history back to the nearest state matching S
          * and apply merge function to reconcile the new state with the outgoing,
          * or redirect back to the root state.
          */
        def using(merge: Merger[S]): UsingMerger = new UsingMerger(merge)

        class UsingMerger private[actions] (merger: Merger[S]) extends Executable {
          override def execute(implicit
            request: Request[_],
            ec: ExecutionContext
          ): Future[Result] = {
            implicit val rc: RequestContext = JourneyController.this.context(request)
            withAuthorised(request) { user: User =>
              JourneyController.this.showStateUsingMerge[S] { case _: S => }(merger)
            }
          }

          /**
            * Display the journey state requested by the type parameter S.
            * If the current state is not of type S,
            * try to rewind the history back to the nearest state matching S
            * and apply merge function to reconcile the new state with the current state.
            * If there exists no matching state S in the history,
            * apply the transition and redirect to the new state.
            * If transition is not allowed then redirect back to the current state.
            * @tparam S type of the state to display
            */
          def orApply(transition: User => Transition): OrApply =
            new OrApply(transition)

          class OrApply private[actions] (transition: User => Transition) extends Executable {
            override def execute(implicit
              request: Request[_],
              ec: ExecutionContext
            ): Future[Result] = {
              implicit val rc: RequestContext = JourneyController.this.context(request)
              withAuthorised(request) { user: User =>
                JourneyController.this.showStateUsingMergeOrApply { case _: S => }(merger)(
                  transition(user)
                )
              }
            }
          }

          /**
            * Display the journey state requested by the type parameter S.
            * If the current state is not of type S,
            * try to rewind the history back to the nearest state matching S
            * and apply merge function to reconcile the new state with the current state.
            * If there exists no matching state S in the history,
            * apply the transition and redirect to the new state.
            * If transition is not allowed then redirect back to the current state.
            * @tparam S type of the state to display
            */
          def orApplyWithRequest(transition: Request[_] => User => Transition): OrApplyWithRequest =
            new OrApplyWithRequest(transition)

          class OrApplyWithRequest private[actions] (transition: Request[_] => User => Transition)
              extends Executable {
            override def execute(implicit
              request: Request[_],
              ec: ExecutionContext
            ): Future[Result] = {
              implicit val rc: RequestContext = JourneyController.this.context(request)
              withAuthorised(request) { user: User =>
                JourneyController.this.showStateUsingMergeOrApply { case _: S => }(merger)(
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
        */
      def apply(transition: User => Transition): Apply = new Apply(transition)

      class Apply private[actions] (transition: User => Transition) extends Executable {
        override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { user: User =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.apply(transition(user), JourneyController.this.redirect)
          }
      }

      /**
        * Apply state transition and, depending on the outcome,
        * display if the new state is the same as previous,
        * or redirect to the URL matching the new state.
        */
      def applyThenRedirectOrDisplay(
        transition: Request[_] => User => Transition
      ): ApplyThenRedirectOrDisplay =
        new ApplyThenRedirectOrDisplay(transition)

      class ApplyThenRedirectOrDisplay private[actions] (
        transition: Request[_] => User => Transition
      ) extends Executable {
        override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { user: User =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this
              .applyThenRedirectOrDisplay(transition(request)(user))
          }
      }

      /**
        * Apply state transition parametrized by the user information
        * and redirect to the URL matching the new state.
        */
      def applyWithRequest(transition: Request[_] => User => Transition): ApplyWithRequest =
        new ApplyWithRequest(transition)

      class ApplyWithRequest private[actions] (transition: Request[_] => User => Transition)
          extends Executable {
        override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
          withAuthorised(request) { user: User =>
            implicit val rc: RequestContext = JourneyController.this.context(request)
            JourneyController.this.apply(transition(request)(user), JourneyController.this.redirect)
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

      class BindForm[Payload] private[actions] (form: Form[Payload]) {

        /**
          * Apply state transition parametrized by the user information and form output
          * and redirect to the URL matching the end state.
          */
        def apply(transition: User => Payload => Transition): Apply =
          new Apply(transition)

        class Apply private[actions] (transition: User => Payload => Transition)
            extends Executable {
          override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
            withAuthorised(request) { user: User =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.bindForm(form, transition(user))
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

        class ApplyWithRequest private[actions] (
          transition: Request[_] => User => Payload => Transition
        ) extends Executable {
          override def execute(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
            withAuthorised(request) { user: User =>
              implicit val rc: RequestContext = JourneyController.this.context(request)
              JourneyController.this.bindForm(form, transition(request)(user))
            }
        }
      }
    }
  }

  //-------------------------------------------------
  // DEPRECATED STUFF
  //-------------------------------------------------

  /**
    * Prefer to use DSL {{{actions.show[ExpectedState]}}}
    */
  protected final def actionShowState(
    expectedStates: ExpectedStates
  )(implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val rc: RequestContext = context(request)
      showState(expectedStates)
    }

  /**
    * Prefer to use DSL {{{actions.whenAuthorised(withAuthorised).show[ExpectedState]}}}
    */
  protected final def actionShowStateWhenAuthorised[User](
    withAuthorised: WithAuthorised[User]
  )(expectedStates: ExpectedStates)(implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val rc: RequestContext = context(request)
      showStateWhenAuthorised(withAuthorised)(expectedStates)
    }
}

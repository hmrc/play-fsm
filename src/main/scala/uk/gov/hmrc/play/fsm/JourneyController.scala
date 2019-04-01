/*
 * Copyright 2019 HM Revenue & Customs
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
  */
trait JourneyController {
  self: Controller =>

  /** This has to be injected in the concrete controller */
  val journeyService: JourneyService

  def hc(implicit rh: RequestHeader): HeaderCarrier

  import journeyService.StateAndBreadcrumbs
  import journeyService.model.{State, Transition, TransitionNotAllowed}

  type Route = Request[_] => Result

  /** implement this to map states into endpoints for redirection and back linking */
  def getCallFor(state: State)(implicit request: Request[_]): Call

  /** implement this to render state after transition or when form validation fails */
  def renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]]): Route

  type RouteFactory = StateAndBreadcrumbs => Route

  /** displays template for the state and breadcrumbs */
  final val display: RouteFactory = (state: StateAndBreadcrumbs) =>
    (request: Request[_]) => renderState(state._1, state._2, None)(request)

  /** redirects to the endpoint matching state */
  final val redirect: RouteFactory =
    (state: StateAndBreadcrumbs) => (request: Request[_]) => Redirect(getCallFor(state._1)(request))

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
    withAuthorised: WithAuthorised[User])(form: Form[Payload])(
    transition: User => Payload => Transition)(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext) =
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
                Redirect(getCallFor(state))
                  .flashing(Flash(formWithErrors.data)))
            case None =>
              apply(journeyService.model.start, redirect)
        },
        userInput => apply(transition(userInput), redirect)
      )

  type ExpectedStates = PartialFunction[State, Unit]

  protected final def showCurrentStateWhen(expectedStates: ExpectedStates)(
    implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val headerCarrier: HeaderCarrier = hc(request)
      for {
        stateAndBreadcrumbsOpt <- journeyService.currentState
        result <- stateAndBreadcrumbsOpt match {
                   case None => apply(journeyService.model.start, redirect)
                   case Some(stateAndBreadcrumbs) =>
                     if (hasMatchingState(expectedStates, stateAndBreadcrumbs))
                       journeyService.currentState
                         .flatMap(stepBackUntil(expectedStates))
                     else apply(journeyService.model.start, redirect)
                 }
      } yield result
    }

  protected final def showCurrentStateWhenAuthorised[User](withAuthorised: WithAuthorised[User])(
    expectedStates: ExpectedStates)(implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val headerCarrier: HeaderCarrier = hc(request)
      withAuthorised(request) { _ =>
        for {
          stateAndBreadcrumbsOpt <- journeyService.currentState
          result <- stateAndBreadcrumbsOpt match {
                     case None => apply(journeyService.model.start, redirect)
                     case Some(stateAndBreadcrumbs) =>
                       if (hasMatchingState(expectedStates, stateAndBreadcrumbs))
                         journeyService.currentState
                           .flatMap(stepBackUntil(expectedStates))
                       else apply(journeyService.model.start, redirect)
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

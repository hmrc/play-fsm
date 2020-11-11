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
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{single, text}
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.util.concurrent.TimeoutException

@Singleton
class DummyJourneyController @Inject() (override val journeyService: DummyJourneyService)(implicit
  ec: ExecutionContext
) extends Controller
    with JourneyController[DummyContext] {

  import DummyJourneyController._
  import journeyService.model.{Mergers, State, Transitions}

  override implicit def context(implicit rh: RequestHeader): DummyContext = DummyContext()

  val asUser: WithAuthorised[Int] = { implicit request => body =>
    body(5)
  }

  // ACTIONS

  val start: Action[AnyContent] = action { implicit request =>
    journeyService
      .cleanBreadcrumbs(_ => Nil)
      .flatMap(_ => apply(journeyService.model.start, redirect))
  }

  val showStart: Action[AnyContent] = actionShowState {
    case State.Start =>
  }

  val showStartDsl: Action[AnyContent] =
    actions
      .show[State.Start.type]
      .orRollback

  val showStartDsl2: Action[AnyContent] =
    actions
      .show[State.Start.type]
      .orRollback
      .andCleanBreadcrumbs()

  val showStartDsl3: Action[AnyContent] =
    actions
      .show[State.Start.type]
      .orRollbackUsing(Mergers.toStart)

  val showStartDsl4: Action[AnyContent] =
    actions
      .show[State.Start.type]
      .orRollbackUsing(Mergers.toStart)
      .redirect

  def continue: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(asUser)(ArgForm)(Transitions.continue)
    }

  def continueDsl: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .bindForm(ArgForm)
      .apply(Transitions.continue)

  def continueDsl2: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .bindForm(ArgForm)
      .applyWithRequest(_ => Transitions.continue)

  val showContinue: Action[AnyContent] = actionShowStateWhenAuthorised(asUser) {
    case State.Continue(_) =>
  }

  val showWithRollbackContinueDsl: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.Continue]
      .orRollback

  val showWithRollbackContinueDsl2: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.Continue]
      .orRollbackUsing(Mergers.toContinue)

  val showWithRollbackOrApplyContinueDsl: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.Continue]
      .orRollback
      .orApply(Transitions.showContinue)

  val showOrApplyContinueDsl: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.Continue]
      .orApply(Transitions.showContinue)

  val showWithRollbackOrApplyContinueDsl2: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.Continue]
      .orRollback
      .orApplyWithRequest(implicit request => Transitions.showContinue)

  val showOrApplyContinueDsl2: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.Continue]
      .orApplyWithRequest(implicit request => Transitions.showContinue)

  val stop: Action[AnyContent] = action { implicit request =>
    whenAuthorised(asUser)(Transitions.stop)(redirect)
  }

  val stopDsl: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .apply(Transitions.stop)

  val stopDsl2: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .applyWithRequest(_ => Transitions.stop)

  val stopDsl3: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .apply(Transitions.stop)
      .redirectOrDisplayIfSame

  val stopDsl4: Action[AnyContent] =
    actions
      .apply(Transitions.stop(555))
      .redirectOrDisplayIfSame

  val stopDsl5: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .applyWithRequest(_ => Transitions.stop)
      .redirectOrDisplayIfSame

  val stopDsl6: Action[AnyContent] =
    actions
      .applyWithRequest(_ => Transitions.stop(555))
      .redirectOrDisplayIfSame

  val stopDsl7: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .apply(Transitions.stop)
      .redirectOrDisplayIf[State.Stop]

  val stopDsl8: Action[AnyContent] =
    actions
      .apply(Transitions.stop(555))
      .redirectOrDisplayIf[State.Stop]

  val stopDsl9: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .applyWithRequest(_ => Transitions.stop)
      .redirectOrDisplayIf[State.Stop]

  val stopDsl10: Action[AnyContent] =
    actions
      .applyWithRequest(_ => Transitions.stop(555))
      .redirectOrDisplayIf[State.Stop]

  val stopDsl11: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .apply(Transitions.stop)
      .redirectOrDisplayIf[State.Continue]

  val stopDsl12: Action[AnyContent] =
    actions
      .apply(Transitions.stop(555))
      .redirectOrDisplayIf[State.Continue]

  val stopDsl13: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .applyWithRequest(_ => Transitions.stop)
      .redirectOrDisplayIf[State.Continue]

  val stopDsl14: Action[AnyContent] =
    actions
      .applyWithRequest(_ => Transitions.stop(555))
      .redirectOrDisplayIf[State.Continue]

  val showStop: Action[AnyContent] = actionShowStateWhenAuthorised(asUser) {
    case State.Stop(_) =>
  }

  val showStopDsl: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.Stop]

  val showStopDsl2: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.Stop]
      .andCleanBreadcrumbs()

  def dummyFx(s: String)(implicit dc: DummyContext): String = s

  def dummyFx2(s: String): String = s

  val showDeadEndDsl: Action[AnyContent] =
    actions
      .show[State.DeadEnd]
      .orRollbackUsing(Mergers.toDeadEnd)
      .orApplyWithRequest(implicit request => Transitions.toDeadEnd(dummyFx))

  val showDeadEndDsl1: Action[AnyContent] =
    actions
      .show[State.DeadEnd]
      .orRollbackUsing(Mergers.toDeadEnd)
      .orApply(Transitions.toDeadEnd(dummyFx2))

  val showDeadEndDsl2: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.DeadEnd]
      .orRollbackUsing(Mergers.toDeadEnd)
      .orApplyWithRequest(implicit request => user => Transitions.toDeadEnd(dummyFx))

  val showDeadEndDsl4: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.DeadEnd]
      .orRollbackUsing(Mergers.toDeadEnd)
      .orApply(user => Transitions.toDeadEnd(dummyFx2))

  val showDeadEndDsl3: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .show[State.DeadEnd]
      .orRollbackUsing(Mergers.toDeadEnd)

  val parseJson1: Action[AnyContent] =
    actions
      .parseJson[TestPayload]
      .apply(Transitions.processPayload)
      .recoverWith(implicit request => { case _ => Future.successful(BadRequest) })

  val parseJson2: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .parseJson[TestPayload](ifFailure = _ => Future.successful(BadRequest))
      .apply(user => Transitions.processPayload)
      .recover { case _ => BadRequest }

  val parseJson3: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .parseJson[TestPayload](ifFailure = _ => Future.successful(BadRequest))
      .apply(user => Transitions.processPayload)
      .displayUsing(implicit request => renderState2)
      .recover { case _ => BadRequest }
      .transform { case BadRequest => NotFound }

  val wait1: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .waitForStateAndDisplay[State.Continue](3)

  val wait2: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .waitForStateAndDisplay[State.Continue](3)
      .recover { case e: TimeoutException => BadRequest }

  val wait3: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .waitForStateAndDisplay[State.Continue](3)
      .orApplyOnTimeout(_ => Transitions.showContinue)

  val wait4: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .waitForStateThenRedirect[State.Continue](3)

  val wait5: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .waitForStateThenRedirect[State.Continue](3)
      .recover { case e: TimeoutException => BadRequest }

  val wait6: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .waitForStateThenRedirect[State.Continue](3)
      .orApplyOnTimeout(_ => Transitions.showContinue)

  val wait7: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .waitForStateThenRedirect[State.Continue](3)
      .orApplyOnTimeout(_ => Transitions.showContinue)
      .display

  val wait8: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .waitForStateThenRedirect[State.Continue](3)
      .orApplyOnTimeout(_ => Transitions.showContinue)
      .redirectOrDisplayIf[State.Continue]

  val wait11: Action[AnyContent] =
    actions
      .waitForStateAndDisplay[State.Continue](3)

  val wait12: Action[AnyContent] =
    actions
      .waitForStateAndDisplay[State.Continue](3)
      .recover { case e: TimeoutException => BadRequest }

  val wait13: Action[AnyContent] =
    actions
      .waitForStateAndDisplay[State.Continue](3)
      .orApplyOnTimeout(_ => Transitions.showContinue(555))

  val wait14: Action[AnyContent] =
    actions
      .waitForStateThenRedirect[State.Continue](3)

  val wait15: Action[AnyContent] =
    actions
      .waitForStateThenRedirect[State.Continue](3)
      .recover { case e: TimeoutException => BadRequest }

  val wait16: Action[AnyContent] =
    actions
      .waitForStateThenRedirect[State.Continue](3)
      .orApplyOnTimeout(_ => Transitions.showContinue(555))

  val wait17: Action[AnyContent] =
    actions
      .waitForStateThenRedirect[State.Continue](3)
      .orApplyOnTimeout(_ => Transitions.showContinue(555))
      .display

  val wait18: Action[AnyContent] =
    actions
      .waitForStateThenRedirect[State.Continue](3)
      .orApplyOnTimeout(_ => Transitions.showContinue(555))
      .redirectOrDisplayIf[State.Continue]

  val current1: Action[AnyContent] =
    actions.showCurrentState

  val current2: Action[AnyContent] =
    actions.showCurrentState
      .displayUsing(implicit request => renderState2)

  val current3: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .showCurrentState

  val current4: Action[AnyContent] =
    actions
      .whenAuthorised(asUser)
      .showCurrentState
      .displayUsing(implicit request => renderState2)

  def backlink1(implicit request: Request[_]) =
    backLinkToMostRecent[State.Continue](
      List(
        State.Start,
        State.Stop("1"),
        State.Continue("1"),
        State.Stop("3"),
        State.Continue("3"),
        State.Continue("2")
      )
    )

  // VIEWS

  /** implement this to map states into endpoints for redirection and back linking */
  override def getCallFor(state: journeyService.model.State)(implicit request: Request[_]): Call =
    state match {
      case State.Start       => Call("GET", "/start")
      case State.Continue(_) => Call("GET", "/continue")
      case State.Stop(_)     => Call("GET", "/stop")
      case State.DeadEnd(_)  => Call("GET", "/dead-end")
    }

  /** implement this to render state after transition or when form validation fails */
  override def renderState(
    state: journeyService.model.State,
    breadcrumbs: List[journeyService.model.State],
    formWithErrors: Option[Form[_]]
  )(implicit request: Request[_]): Result =
    state match {
      case State.Start =>
        Ok(Html(s"""Start | <a href="${backLinkFor(breadcrumbs).url}">back</a>"""))
      case State.Continue(arg) if arg.nonEmpty =>
        Ok(s"Continue with $arg and form")
      case State.Continue(arg) =>
        Ok(s"Continue with $arg and form")
      case State.Stop(result)    => Ok(s"Result is $result")
      case State.DeadEnd(result) => Ok(s"Dead end: $result")
    }

  def renderState2(
    state: journeyService.model.State,
    breadcrumbs: List[journeyService.model.State],
    formWithErrors: Option[Form[_]]
  )(implicit request: Request[_]): Result =
    state match {
      case State.Start           => Ok("Start")
      case State.Continue(arg)   => Created("Continue")
      case State.Stop(result)    => Accepted("Stop")
      case State.DeadEnd(result) => NotFound("DeadEnd")
    }
}

object DummyJourneyController {

  val ArgForm: Form[String] = Form(
    single(
      "arg" -> text
    )
  )

}

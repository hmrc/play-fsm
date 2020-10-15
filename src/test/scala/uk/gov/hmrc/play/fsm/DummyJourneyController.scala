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
import uk.gov.hmrc.play.fsm.OptionalFormOps._

import scala.concurrent.ExecutionContext

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
    actions.show[State.Start.type]

  val showStartDsl2: Action[AnyContent] =
    actions.show[State.Start.type].andCleanBreadcrumbs()

  val showStartDsl3: Action[AnyContent] =
    actions.show[State.Start.type].using(Mergers.toStart)

  def continue: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(asUser)(ArgForm)(Transitions.continue)
    }

  def continueDsl: Action[AnyContent] =
    actions.whenAuthorised(asUser).bindForm(ArgForm).apply(Transitions.continue)

  val showContinue: Action[AnyContent] = actionShowStateWhenAuthorised(asUser) {
    case State.Continue(_) =>
  }

  val showContinueDsl: Action[AnyContent] =
    actions.whenAuthorised(asUser).show[State.Continue]

  val showContinueDsl2: Action[AnyContent] =
    actions.whenAuthorised(asUser).show[State.Continue].using(Mergers.toContinue)

  val showOrApplyContinueDsl: Action[AnyContent] =
    actions.whenAuthorised(asUser).show[State.Continue].orApply(Transitions.showContinue)

  val stop: Action[AnyContent] = action { implicit request =>
    whenAuthorised(asUser)(Transitions.stop)(redirect)
  }

  val stopDsl: Action[AnyContent] = actions.whenAuthorised(asUser).apply(Transitions.stop)

  val showStop: Action[AnyContent] = actionShowStateWhenAuthorised(asUser) {
    case State.Stop(_) =>
  }

  val showStopDsl: Action[AnyContent] = actions.whenAuthorised(asUser).show[State.Stop]

  val showStopDsl2: Action[AnyContent] =
    actions.whenAuthorised(asUser).show[State.Stop].andCleanBreadcrumbs()

  // VIEWS

  /** implement this to map states into endpoints for redirection and back linking */
  override def getCallFor(state: journeyService.model.State)(implicit request: Request[_]): Call =
    state match {
      case State.Start       => Call("GET", "/start")
      case State.Continue(_) => Call("GET", "/continue")
      case State.Stop(_)     => Call("GET", "/stop")
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
      case State.Continue(arg) => Ok(s"Continue with $arg and form ${formWithErrors.or(ArgForm)}")
      case State.Stop(result)  => Ok(s"Result is $result")
    }
}

object DummyJourneyController {

  val ArgForm: Form[String] = Form(
    single(
      "arg" -> text
    )
  )

}

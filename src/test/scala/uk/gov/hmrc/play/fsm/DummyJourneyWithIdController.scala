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

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.mvc._
import play.twirl.api.Html
import uk.gov.hmrc.play.fsm.OptionalFormOps._

import scala.concurrent.ExecutionContext
import scala.util.Success

@Singleton
class DummyJourneyWithIdController @Inject() (
  override val journeyService: DummyJourneyService,
  override val actionBuilder: DefaultActionBuilder,
  override val controllerComponents: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends InjectedController
    with JourneyController[DummyContext]
    with JourneyIdSupport[DummyContext] {

  import DummyJourneyController._
  import journeyService.model.{State, Transitions}

  override def amendContext(rc: DummyContext)(key: String, value: String): DummyContext = rc

  override implicit def context(implicit rh: RequestHeader): DummyContext =
    appendJourneyId(DummyContext.default)

  val asUser: WithAuthorised[Int] = { implicit request => body =>
    body(5)
  }

  // ACTIONS

  val start: Action[AnyContent] = action { implicit request =>
    journeyService
      .cleanBreadcrumbs(_ => Nil)
      .flatMap(_ => helpers.apply(journeyService.model.start, helpers.display))
  }

  val showStart: Action[AnyContent] = legacy.actionShowState {
    case State.Start =>
  }

  def continue: Action[AnyContent] =
    action { implicit request =>
      legacy.whenAuthorisedWithForm(asUser)(ArgForm)(Transitions.continue)
    }

  val showContinue: Action[AnyContent] =
    legacy.actionShowStateWhenAuthorised(asUser) {
      case State.Continue(_) =>
    }

  val stop: Action[AnyContent] = action { implicit request =>
    legacy.whenAuthorised(asUser)(Transitions.stop)(helpers.redirect)
  }

  val showStop: Action[AnyContent] = action { implicit request =>
    legacy
      .showStateWhenAuthorised(asUser) {
        case State.Stop(_) =>
      }
      .andThen {
        case Success(_) => journeyService.cleanBreadcrumbs()
      }
  }

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

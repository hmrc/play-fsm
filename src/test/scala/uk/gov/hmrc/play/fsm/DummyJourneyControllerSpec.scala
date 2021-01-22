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

import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Flash
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import play.api.mvc.AnyContent
import play.api.libs.json.Json
import akka.stream.Materializer
import scala.concurrent.Future
import java.util.concurrent.TimeoutException

class DummyJourneyControllerSpec
    extends UnitSpec
    with OneAppPerSuite
    with StateAndBreadcrumbsMatchers {

  import scala.concurrent.duration._
  implicit override val defaultTimeout: FiniteDuration = 360 seconds

  implicit val context: DummyContext = DummyContext()

  override lazy val app: Application = new GuiceApplicationBuilder().build()

  lazy val journeyState: DummyJourneyService = app.injector.instanceOf[DummyJourneyService]
  lazy val controller: DummyJourneyController =
    app.injector.instanceOf[DummyJourneyController]

  implicit lazy val materializer: Materializer = app.materializer

  import journeyState.model.State

  def fakeRequest = FakeRequest()

  "DummyJourneyController" should {

    "stay in Start when doNothing" in {
      journeyState.set(State.Start, Nil)
      val result = controller.doNothing(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)

    }

    "stay in Continue when doNothing" in {
      journeyState.set(State.Continue("foo"), Nil)
      val result = controller.doNothing(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Continue("foo"), Nil)
    }

    "stay in Stop when doNothing" in {
      journeyState.set(State.Stop("baz"), Nil)
      val result = controller.doNothing(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Stop("baz"), Nil)
    }

    "given none when oldStart then redirect to Start" in {
      journeyState.clear
      val result = controller.oldStart(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given none when oldShowStart then redirect to Start" in {
      journeyState.clear
      val result = controller.oldShowStart(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given none when showStartOrRollback then redirect to Start" in {
      journeyState.clear
      val result = controller.showStartOrRollback(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given none when showStartOrRollbackAndCleanBreadcrumbs then redirect to Start" in {
      journeyState.clear
      val result = controller.showStartOrRollbackAndCleanBreadcrumbs(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given none when showStartOrRollbackUsingMerger goto Start" in {
      journeyState.clear
      val result = controller.showStartOrRollbackUsingMerger(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given none when showStartOrRollbackUsingMergerViaRedirect then redirect to Start" in {
      journeyState.clear
      val result = controller.showStartOrRollbackUsingMergerViaRedirect(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given none when showStartOrRedirectToCurrentState then redirect to Start" in {
      journeyState.clear
      val result = controller.showStartOrRedirectToCurrentState(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given Start when showStart then display Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.oldShowStart(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Start when showStartOrRollback then display Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartOrRollback(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Start when showStartOrRollbackAndCleanBreadcrumbs then display Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartOrRollbackAndCleanBreadcrumbs(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Start when showStartOrRollbackUsingMerger then display Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartOrRollbackUsingMerger(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Start when showStartOrRollbackUsingMergerViaRedirect then redirect to Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartOrRollbackUsingMergerViaRedirect(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Start when showStartOrRedirectToCurrentState then display Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartOrRedirectToCurrentState(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Continue when oldShowStart then display most recent Start" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.oldShowStart(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Continue when showStartOrRollback then display most recent Start" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStartOrRollback(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Continue when showStartOrRollbackAndCleanBreadcrumbs then display most recent Start" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStartOrRollbackAndCleanBreadcrumbs(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Continue when showStartOrRollbackUsingMerger then display most recent Start" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStartOrRollbackUsingMerger(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "given Continue when showStartOrRedirectToCurrentState then redirect to Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStartOrRedirectToCurrentState(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start when oldContinue then redirect to Continue" in {
      journeyState.set(State.Start, Nil)
      val result = controller.oldContinue(fakeRequest.withFormUrlEncodedBody("arg" -> "dummy"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start bindFormAndApplyTransitionContinue then redirect to Continue" in {
      journeyState.set(State.Start, Nil)
      val result = controller.bindFormAndApplyTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("arg" -> "dummy")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start bindFormAndApplyWithRequestTransitionContinue then redirect to Continue" in {
      journeyState.set(State.Start, Nil)
      val result = controller.bindFormAndApplyWithRequestTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("arg" -> "dummy")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start whenAuthorisedBindFormAndApplyTransitionContinue then redirect to Continue" in {
      journeyState.set(State.Start, Nil)
      val result = controller.whenAuthorisedBindFormAndApplyTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("arg" -> "dummy")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start whenAuthorisedBindFormAndApplyWithRequestTransitionContinue then redirect to Continue" in {
      journeyState.set(State.Start, Nil)
      val result = controller.whenAuthorisedBindFormAndApplyWithRequestTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("arg" -> "dummy")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start bindFormDerivedFromStateAndApplyTransitionContinue then redirect to Continue" in {
      journeyState.set(State.Start, Nil)
      val result = controller.bindFormDerivedFromStateAndApplyTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("arg" -> "dummy")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start bindFormDerivedFromStateAndApplyWithRequestTransitionContinue then redirect to Continue" in {
      journeyState.set(State.Start, Nil)
      val result = controller.bindFormDerivedFromStateAndApplyWithRequestTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("arg" -> "dummy")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start whenAuthorisedBindFormDerivedFromStateAndApplyTransitionContinue then redirect to Continue" in {
      journeyState.set(State.Start, Nil)
      val result = controller.whenAuthorisedBindFormDerivedFromStateAndApplyTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("arg" -> "dummy")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start whenAuthorisedBindFormDerivedFromStateAndApplyWithRequestTransitionContinue then redirect to Continue" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedBindFormDerivedFromStateAndApplyWithRequestTransitionContinue(
          fakeRequest.withFormUrlEncodedBody("arg" -> "dummy")
        )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Start when oldContinue then redirect to Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.oldContinue(fakeRequest.withFormUrlEncodedBody())
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given Start when whenAuthorisedBindFormAndApplyTransitionContinue then redirect to Start" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedBindFormAndApplyTransitionContinue(
          fakeRequest.withFormUrlEncodedBody()
        )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given Start when whenAuthorisedBindFormDerivedFromStateAndApplyTransitionContinue then redirect to Start" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedBindFormDerivedFromStateAndApplyTransitionContinue(
          fakeRequest.withFormUrlEncodedBody()
        )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "given Continue when oldContinue then redirect to Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.oldContinue(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get should have[State](
        State.Continue("dummy,foo"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "given Continue whenAuthorisedBindFormAndApplyTransitionContinue then redirect to Continue " in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.whenAuthorisedBindFormAndApplyTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("arg" -> "foo")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get should have[State](
        State.Continue("dummy,foo"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "given Continue when oldContinue then redirect to Continue with flashed params" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.oldContinue(fakeRequest.withFormUrlEncodedBody("foo" -> "arg"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      flash(result)            shouldBe Flash(Map("foo" -> "arg"))
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedBindFormAndApplyTransitionContinue then redirect to Continue with flashed params" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.whenAuthorisedBindFormAndApplyTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("foo" -> "arg")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      flash(result)            shouldBe Flash(Map("foo" -> "arg"))
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Stop when oldContinue then redirect back to Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.oldContinue(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "given Stop whenAuthorisedBindFormAndApplyTransitionContinue then redirect back to Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.whenAuthorisedBindFormAndApplyTransitionContinue(
        fakeRequest.withFormUrlEncodedBody("arg" -> "foo")
      )
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "given Continue when oldShowContinue then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.oldShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedShowContinueOrRollback then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.whenAuthorisedShowContinueOrRollback(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedShowContinueOrRollbackUsing then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.whenAuthorisedShowContinueOrRollbackUsing(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Stop when oldShowContinue then display Continue" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.oldShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Stop whenAuthorisedShowContinueOrRollback then display Continue" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.whenAuthorisedShowContinueOrRollback(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "giben Start whenAuthorisedShowContinueOrRollbackOrApplyTransitionShowContinue then display Continue" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedShowContinueOrRollbackOrApplyTransitionShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "given Start whenAuthorisedShowContinueOrApplyShowContinue then display Continue" in {
      journeyState.set(State.Start, Nil)
      val result = controller.whenAuthorisedShowContinueOrApplyShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "given Start whenAuthorisedShowContinueOrRollbackOrApplyWithRequestTransitionShowContinue then display Continue" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedShowContinueOrRollbackOrApplyWithRequestTransitionShowContinue(
          fakeRequest
        )
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "given Start whenAuthorisedShowContinueOrApplyWithRequestTransitionShowContinue then display Continue" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedShowContinueOrApplyWithRequestTransitionShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "given Start whenAuthorisedShowContinueOrRedirectToCurrentState then redirect to Start" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedShowContinueOrRedirectToCurrentState(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Start,
        Nil
      )
    }

    "given Start whenAuthorisedShowContinueOrRedirectToStart then redirect to Start" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedShowContinueOrRedirectToStart(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Start,
        Nil
      )
    }

    "given Start whenAuthorisedShowContinueOrReturn then return 406" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedShowContinueOrReturn(fakeRequest)
      status(result) shouldBe 406
      journeyState.get should have[State](
        State.Start,
        Nil
      )
    }

    "given Start whenAuthorisedShowContinueOrRedirectTo then redirect to /dummy" in {
      journeyState.set(State.Start, Nil)
      val result =
        controller.whenAuthorisedShowContinueOrRedirectTo(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Start,
        Nil
      )
    }

    "given Continue whenAuthorisedShowContinueOrRollbackOrApplyTransitionShowContinue then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRollbackOrApplyTransitionShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedShowContinueOrApplyShowContinue then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.whenAuthorisedShowContinueOrApplyShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedShowContinueOrRollbackOrApplyWithRequestTransitionShowContinue then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRollbackOrApplyWithRequestTransitionShowContinue(
          fakeRequest
        )
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedShowContinueOrApplyWithRequestTransitionShowContinue then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrApplyWithRequestTransitionShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedShowContinueOrRedirectToCurrentState then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRedirectToCurrentState(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedShowContinueOrRedirectToStart then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRedirectToStart(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedShowContinueOrReturn then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrReturn(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Continue whenAuthorisedShowContinueOrRedirectTo then display Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRedirectTo(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "given Stop whenAuthorisedShowContinueOrRollbackOrApplyTransitionShowContinue then rollback to Continue" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRollbackOrApplyTransitionShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("dummy"),
        List(State.Start)
      )
    }

    "given Stop whenAuthorisedShowContinueOrApplyShowContinue choose apply and display Continue" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.whenAuthorisedShowContinueOrApplyShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("ymmud"),
        List(State.Stop("dummy"), State.Continue("dummy"), State.Start)
      )
    }

    "given Stop whenAuthorisedShowContinueOrRollbackOrApplyWithRequestTransitionShowContinue then rollback to Continue" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRollbackOrApplyWithRequestTransitionShowContinue(
          fakeRequest
        )
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("dummy"),
        List(State.Start)
      )
    }

    "given Stop whenAuthorisedShowContinueOrApplyWithRequestTransitionShowContinue choose apply and display Continue" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrApplyWithRequestTransitionShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("ymmud"),
        List(State.Stop("dummy"), State.Continue("dummy"), State.Start)
      )
    }

    "given Stop whenAuthorisedShowContinueOrRedirectToCurrentState then redirect to Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRedirectToCurrentState(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "given Stop whenAuthorisedShowContinueOrRedirectToStart then redirect to Start" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRedirectToStart(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Start,
        Nil
      )
    }

    "given Stop whenAuthorisedShowContinueOrReturn then return 406" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrReturn(fakeRequest)
      status(result) shouldBe 406
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "given Stop whenAuthorisedShowContinueOrRedirectTo then redirect to /dummy" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result =
        controller.whenAuthorisedShowContinueOrRedirectTo(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl+merge: after GET /continue show merged Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.whenAuthorisedShowContinueOrRollbackUsing(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("dummy_dummy"),
        List(State.Start)
      )
    }

    "after GET /continue go to Start when in Stop but no breadcrumbs" in {
      journeyState.set(State.Stop("dummy"), Nil)
      val result = controller.oldShowContinue(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl: after GET /continue go to Start when in Stop but no breadcrumbs" in {
      journeyState.set(State.Stop("dummy"), Nil)
      val result = controller.whenAuthorisedShowContinueOrRollback(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl2: after GET /continue show new Continue when in Stop but no breadcrumbs" in {
      journeyState.set(State.Stop("dummy"), Nil)
      val result =
        controller.whenAuthorisedShowContinueOrRollbackOrApplyTransitionShowContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("ymmud"), List(State.Stop("dummy")))
    }

    "after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.oldStop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "dsl: after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.whenAuthorisedApplyTransitionStop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "dsl2: after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.whenAuthorisedApplyWithRequestTransitionStop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "dsl3: after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.whenAuthorisedApplyTransitionStopRedirectOrDisplayIfSame(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.oldStop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.whenAuthorisedApplyTransitionStop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl2: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.whenAuthorisedApplyWithRequestTransitionStop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl3: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.whenAuthorisedApplyTransitionStopRedirectOrDisplayIfSame(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl4: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.applyTransitionStopRedirectOrDisplayIfSame(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl5: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl5(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl6: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl6(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl7: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl7(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl8: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl8(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl9: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl9(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl10: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl10(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl11: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl11(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl12: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl12(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl13: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl13(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl14: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl14(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "after POST /stop stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.oldStop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl: after POST /stop stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.whenAuthorisedApplyTransitionStop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl2: after POST /stop stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.whenAuthorisedApplyWithRequestTransitionStop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl3: after POST /stop stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.whenAuthorisedApplyTransitionStopRedirectOrDisplayIfSame(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl+clean: after GET /stop show Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.showStopDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Stop("dummy"), Nil)
    }

    "after GET /stop show Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.showStop(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl: after GET /stop show Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.showStopDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl: after GET /dead-end go to the new DeadEnd when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showDeadEndDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.DeadEnd("empty"), List(State.Start))
    }

    "dsl: after GET /dead-end show existing DeadEnd when in DeadEnd" in {
      journeyState.set(State.DeadEnd("here"), List(State.Start))
      val result = controller.showDeadEndDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.DeadEnd("here"), List(State.Start))
    }

    "dsl: after GET /dead-end go to new empty DeadEnd when in Continue and not DeadEnd in history" in {
      journeyState.set(State.Continue("mummy"), List(State.Start))
      val result = controller.showDeadEndDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.DeadEnd("empty"),
        List(State.Continue("mummy"), State.Start)
      )
    }

    "dsl: after GET /dead-end go to Stop when in Continue(stop) and not DeadEnd in history" in {
      journeyState.set(State.Continue("stop"), List(State.Start))
      val result = controller.showDeadEndDsl(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Stop("continue"),
        List(State.Continue("stop"), State.Start)
      )
    }

    "dsl: after GET /dead-end rollback to DeadEnd when in Continue and DeadEnd in history" in {
      journeyState.set(State.Continue("mummy"), List(State.DeadEnd("empty"), State.Start))
      val result = controller.showDeadEndDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.DeadEnd("mummy"),
        List(State.Start)
      )
    }

    "dsl: after GET /dead-end rollback to DeadEnd when in Continue(stop) and DeadEnd in history" in {
      journeyState.set(State.Continue("stop"), List(State.DeadEnd("empty"), State.Start))
      val result = controller.showDeadEndDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.DeadEnd("stop"),
        List(State.Start)
      )
    }

    "dsl2: after GET /dead-end go to the new DeadEnd when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showDeadEndDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.DeadEnd("empty"), List(State.Start))
    }

    "dsl2: after GET /dead-end show existing DeadEnd when in DeadEnd" in {
      journeyState.set(State.DeadEnd("here"), List(State.Start))
      val result = controller.showDeadEndDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.DeadEnd("here"), List(State.Start))
    }

    "dsl2: after GET /dead-end go to new empty DeadEnd when in Continue and not DeadEnd in history" in {
      journeyState.set(State.Continue("mummy"), List(State.Start))
      val result = controller.showDeadEndDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.DeadEnd("empty"),
        List(State.Continue("mummy"), State.Start)
      )
    }

    "dsl2: after GET /dead-end go to Stop when in Continue(stop) and not DeadEnd in history" in {
      journeyState.set(State.Continue("stop"), List(State.Start))
      val result = controller.showDeadEndDsl2(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Stop("continue"),
        List(State.Continue("stop"), State.Start)
      )
    }

    "dsl2: after GET /dead-end rollback to DeadEnd when in Continue and DeadEnd in history" in {
      journeyState.set(State.Continue("mummy"), List(State.DeadEnd("empty"), State.Start))
      val result = controller.showDeadEndDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.DeadEnd("mummy"),
        List(State.Start)
      )
    }

    "dsl2: after GET /dead-end rollback to DeadEnd when in Continue(stop) and DeadEnd in history" in {
      journeyState.set(State.Continue("stop"), List(State.DeadEnd("empty"), State.Start))
      val result = controller.showDeadEndDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.DeadEnd("stop"),
        List(State.Start)
      )
    }

    "dsl3: after GET /dead-end redirect back to Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showDeadEndDsl3(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl3: after GET /dead-end show existing DeadEnd when in DeadEnd" in {
      journeyState.set(State.DeadEnd("here"), List(State.Start))
      val result = controller.showDeadEndDsl3(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.DeadEnd("here"), List(State.Start))
    }

    "dsl3: after GET /dead-end redirect back to Start when in Continue and not DeadEnd in history" in {
      journeyState.set(State.Continue("mummy"), List(State.Start))
      val result = controller.showDeadEndDsl3(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl3: after GET /dead-end go to Stop when in Continue(stop) and not DeadEnd in history" in {
      journeyState.set(State.Continue("stop"), List(State.Start))
      val result = controller.showDeadEndDsl3(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl3: after GET /dead-end rollback to DeadEnd when in Continue and DeadEnd in history" in {
      journeyState.set(State.Continue("mummy"), List(State.DeadEnd("empty"), State.Start))
      val result = controller.showDeadEndDsl3(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.DeadEnd("mummy"),
        List(State.Start)
      )
    }

    "dsl3: after GET /dead-end rollback to DeadEnd when in Continue(stop) and DeadEnd in history" in {
      journeyState.set(State.Continue("stop"), List(State.DeadEnd("empty"), State.Start))
      val result = controller.showDeadEndDsl3(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.DeadEnd("stop"),
        List(State.Start)
      )
    }

    "dsl: after POST /payload go to Continue with new message" in {
      journeyState.set(State.Continue("stop"), List(State.Start))
      val result = controller.parseJson1(
        fakeRequest
          .withBody[AnyContent](AnyContent(Json.parse("""{"msg":"hello"}""")))
      )
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("hello"),
        List(State.Continue("stop"), State.Start)
      )
    }

    "dsl: after POST /payload show BadRequest if wrong payload" in {
      journeyState.set(State.Continue("stop"), List(State.Start))
      val result = controller.parseJson1(
        fakeRequest
          .withBody[AnyContent](AnyContent("""{"msg":}"""))
      )
      status(result) shouldBe 400
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl2: after POST /payload go to Continue with new message" in {
      journeyState.set(State.Continue("stop"), List(State.Start))
      val result = controller.parseJson2(
        fakeRequest
          .withBody[AnyContent](AnyContent(Json.parse("""{"msg":"hello"}""")))
      )
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("hello"),
        List(State.Continue("stop"), State.Start)
      )
    }

    "dsl2: after POST /payload show BadRequest if wrong payload" in {
      journeyState.set(State.Continue("stop"), List(State.Start))
      val result = controller.parseJson2(
        fakeRequest
          .withBody[AnyContent](AnyContent("""{"msg":}"""))
      )
      status(result) shouldBe 400
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl3: after POST /payload go to Continue with new message" in {
      journeyState.set(State.Continue("stop"), List(State.Start))
      val result = controller.parseJson3(
        fakeRequest
          .withBody[AnyContent](AnyContent(Json.parse("""{"msg":"hello"}""")))
      )
      status(result)        shouldBe 201
      bodyOf(await(result)) shouldBe "Continue"
      journeyState.get should have[State](
        State.Continue("hello"),
        List(State.Continue("stop"), State.Start)
      )
    }

    "dsl3: after POST /payload show BadRequest if wrong payload" in {
      journeyState.set(State.Continue("stop"), List(State.Start))
      val result = controller.parseJson3(
        fakeRequest
          .withBody[AnyContent](AnyContent("""{"msg":}"""))
      )
      status(result) shouldBe 404
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl1: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait1(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl1_1: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait1_1(fakeRequest)
      status(result) shouldBe 201
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl1: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      an[TimeoutException] shouldBe thrownBy {
        await(controller.wait1(fakeRequest))
      }
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl1_1: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      an[TimeoutException] shouldBe thrownBy {
        await(controller.wait1_1(fakeRequest))
      }
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl2: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl2: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait2(fakeRequest)
      status(result) shouldBe 400
      journeyState.get should have[State](
        State.Start,
        Nil
      )
    }

    "dsl3: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait3(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl3: after GET /wait show Continue when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait3(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "dsl4: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait4(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl4: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      an[TimeoutException] shouldBe thrownBy {
        await(controller.wait4(fakeRequest))
      }
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl5: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait5(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl5: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait5(fakeRequest)
      status(result) shouldBe 400
      journeyState.get should have[State](
        State.Start,
        Nil
      )
    }

    "dsl6: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait6(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl6: after GET /wait show Continue when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait6(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "dsl7: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait7(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl7: after GET /wait show Continue when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait7(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "dsl8: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait8(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl8: after GET /wait show Continue when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait8(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "dsl11: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait11(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl11_1: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait11_1(fakeRequest)
      status(result) shouldBe 201
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl11: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      an[TimeoutException] shouldBe thrownBy {
        await(controller.wait11(fakeRequest))
      }
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl11_1: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      an[TimeoutException] shouldBe thrownBy {
        await(controller.wait11_1(fakeRequest))
      }
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl12: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait12(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl12: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait12(fakeRequest)
      status(result) shouldBe 400
      journeyState.get should have[State](
        State.Start,
        Nil
      )
    }

    "dsl13: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait13(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl13: after GET /wait show Continue when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait13(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "dsl14: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait14(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl14: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      an[TimeoutException] shouldBe thrownBy {
        await(controller.wait14(fakeRequest))
      }
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl15: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait15(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl15: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait15(fakeRequest)
      status(result) shouldBe 400
      journeyState.get should have[State](
        State.Start,
        Nil
      )
    }

    "dsl16: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait16(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl16: after GET /wait show Continue when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait16(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "dsl17: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait17(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl17: after GET /wait show Continue when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait17(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "dsl18: after GET /wait show Continue when ready" in {
      journeyState.set(State.Start, Nil)
      Schedule(1000) {
        Future {
          journeyState.set(State.Continue("stop"), List(State.Start))
        }
      }
      val result = controller.wait18(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("stop"),
        List(State.Start)
      )
    }

    "dsl18: after GET /wait show Continue when timeout" in {
      journeyState.set(State.Start, Nil)
      val result = controller.wait18(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "dsl: after GET /current show current Start state" in {
      journeyState.set(State.Start, Nil)
      val result = controller.current1(fakeRequest)
      status(result)        shouldBe 200
      bodyOf(await(result)) shouldBe """Start | <a href="/start">back</a>"""
      journeyState.get        should have[State](State.Start, Nil)
    }

    "dsl: after GET /current show current Continue state" in {
      journeyState.set(State.Continue("foo"), List(State.Start))
      val result = controller.current1(fakeRequest)
      status(result)        shouldBe 200
      bodyOf(await(result)) shouldBe "Continue with foo and form"
      journeyState.get        should have[State](State.Continue("foo"), List(State.Start))
    }

    "dsl: after GET /current show current Stop state" in {
      journeyState.set(State.Stop("bar"), List(State.Continue("foo"), State.Start))
      val result = controller.current1(fakeRequest)
      status(result)        shouldBe 200
      bodyOf(await(result)) shouldBe """Result is bar"""
      journeyState.get should have[State](
        State.Stop("bar"),
        List(State.Continue("foo"), State.Start)
      )
    }

    "dsl2: after GET /current show current Start state" in {
      journeyState.set(State.Start, Nil)
      val result = controller.current2(fakeRequest)
      status(result)        shouldBe 200
      bodyOf(await(result)) shouldBe """Start"""
      journeyState.get        should have[State](State.Start, Nil)
    }

    "dsl2: after GET /current show current Continue state" in {
      journeyState.set(State.Continue("foo"), List(State.Start))
      val result = controller.current2(fakeRequest)
      status(result)        shouldBe 201
      bodyOf(await(result)) shouldBe "Continue"
      journeyState.get        should have[State](State.Continue("foo"), List(State.Start))
    }

    "dsl2: after GET /current show current Stop state" in {
      journeyState.set(State.Stop("bar"), List(State.Continue("foo"), State.Start))
      val result = controller.current2(fakeRequest)
      status(result)        shouldBe 202
      bodyOf(await(result)) shouldBe """Stop"""
      journeyState.get should have[State](
        State.Stop("bar"),
        List(State.Continue("foo"), State.Start)
      )
    }

    "dsl3: after GET /current show current Start state" in {
      journeyState.set(State.Start, Nil)
      val result = controller.current3(fakeRequest)
      status(result)        shouldBe 200
      bodyOf(await(result)) shouldBe """Start | <a href="/start">back</a>"""
      journeyState.get        should have[State](State.Start, Nil)
    }

    "dsl3: after GET /current show current Continue state" in {
      journeyState.set(State.Continue("foo"), List(State.Start))
      val result = controller.current3(fakeRequest)
      status(result)        shouldBe 200
      bodyOf(await(result)) shouldBe "Continue with foo and form"
      journeyState.get        should have[State](State.Continue("foo"), List(State.Start))
    }

    "dsl3: after GET /current show current Stop state" in {
      journeyState.set(State.Stop("bar"), List(State.Continue("foo"), State.Start))
      val result = controller.current3(fakeRequest)
      status(result)        shouldBe 200
      bodyOf(await(result)) shouldBe """Result is bar"""
      journeyState.get should have[State](
        State.Stop("bar"),
        List(State.Continue("foo"), State.Start)
      )
    }

    "dsl4: after GET /current show current Start state" in {
      journeyState.set(State.Start, Nil)
      val result = controller.current4(fakeRequest)
      status(result)        shouldBe 200
      bodyOf(await(result)) shouldBe """Start"""
      journeyState.get        should have[State](State.Start, Nil)
    }

    "dsl4: after GET /current show current Continue state" in {
      journeyState.set(State.Continue("foo"), List(State.Start))
      val result = controller.current4(fakeRequest)
      status(result)        shouldBe 201
      bodyOf(await(result)) shouldBe "Continue"
      journeyState.get        should have[State](State.Continue("foo"), List(State.Start))
    }

    "dsl4: after GET /current show current Stop state" in {
      journeyState.set(State.Stop("bar"), List(State.Continue("foo"), State.Start))
      val result = controller.current4(fakeRequest)
      status(result)        shouldBe 202
      bodyOf(await(result)) shouldBe """Stop"""
      journeyState.get should have[State](
        State.Stop("bar"),
        List(State.Continue("foo"), State.Start)
      )
    }

    "find latest backlink to Continue" in {
      implicit val request = fakeRequest
      controller.backlink1.url shouldBe "/continue"
    }

  }

}

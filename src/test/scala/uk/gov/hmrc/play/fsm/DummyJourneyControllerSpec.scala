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
    "after POST /start transition to Start" in {
      journeyState.clear
      val result = controller.start(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "after GET /start transition to Start when uninitialized" in {
      journeyState.clear
      val result = controller.showStart(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "dsl: after GET /start transition to Start when uninitialized" in {
      journeyState.clear
      val result = controller.showStartDsl(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "dsl+clean: after GET /start transition to Start when uninitialized" in {
      journeyState.clear
      val result = controller.showStartDsl2(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "dsl+merge: after GET /start transition to Start when uninitialized" in {
      journeyState.clear
      val result = controller.showStartDsl3(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "dsl+merge+redirect: after GET /start transition to Start when uninitialized" in {
      journeyState.clear
      val result = controller.showStartDsl4(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "after GET /start show Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStart(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl: after GET /start show Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl+clean: after GET /start show Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl+merge: after GET /start show Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartDsl3(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl+merge+redirect: after GET /start show Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartDsl4(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)
    }

    "after GET /start show previous Start when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStart(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl: after GET /start show previous Start when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStartDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl+clean: after GET /start show previous Start when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStartDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl+merge: after GET /start show previous Start when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStartDsl3(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "after POST /continue transition to Continue when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody("arg" -> "dummy"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl: after POST /continue transition to Continue when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody("arg" -> "dummy"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "after invalid POST /continue stay in Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody())
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "dsl: after invalid POST /continue stay in Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody())
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "after POST /continue transition to Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get should have[State](
        State.Continue("dummy,foo"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl: after POST /continue transition to Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get should have[State](
        State.Continue("dummy,foo"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "after invalid POST /continue stay in Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody("foo" -> "arg"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      flash(result)            shouldBe Flash(Map("foo" -> "arg"))
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl: after invalid POST /continue stay in Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody("foo" -> "arg"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      flash(result)            shouldBe Flash(Map("foo" -> "arg"))
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "after POST /continue stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl: after POST /continue stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl: after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showWithRollbackContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl+merge: after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showWithRollbackContinueDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "after GET /continue show previous Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl: after GET /continue show previous Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showWithRollbackContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "show+rollback+apply: after GET /continue show new Continue when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showWithRollbackOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "show+apply: after GET /continue show new Continue when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "show+rollback+apply2: after GET /continue show new Continue when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showWithRollbackOrApplyContinueDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "show+apply2: after GET /continue show new Continue when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showOrApplyContinueDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("yummy"),
        List(State.Start)
      )
    }

    "show+rollback+apply: after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showWithRollbackOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "show+apply: after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "show+rollback+apply2: after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showWithRollbackOrApplyContinueDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "show+apply2: after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showOrApplyContinueDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "show+rollback+apply: after GET /continue show new Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showWithRollbackOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("dummy"),
        List(State.Start)
      )
    }

    "show+apply: after GET /continue show new Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("ymmud"),
        List(State.Stop("dummy"), State.Continue("dummy"), State.Start)
      )
    }

    "show+rollback+apply2: after GET /continue show new Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showWithRollbackOrApplyContinueDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("dummy"),
        List(State.Start)
      )
    }

    "show+apply2: after GET /continue show new Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showOrApplyContinueDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("ymmud"),
        List(State.Stop("dummy"), State.Continue("dummy"), State.Start)
      )
    }

    "dsl+merge: after GET /continue show merged Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showWithRollbackContinueDsl2(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](
        State.Continue("dummy_dummy"),
        List(State.Start)
      )
    }

    "after GET /continue go to Start when in Stop but no breadcrumbs" in {
      journeyState.set(State.Stop("dummy"), Nil)
      val result = controller.showContinue(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl: after GET /continue go to Start when in Stop but no breadcrumbs" in {
      journeyState.set(State.Stop("dummy"), Nil)
      val result = controller.showWithRollbackContinueDsl(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl2: after GET /continue show new Continue when in Stop but no breadcrumbs" in {
      journeyState.set(State.Stop("dummy"), Nil)
      val result = controller.showWithRollbackOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("ymmud"), List(State.Stop("dummy")))
    }

    "after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.stop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "dsl: after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.stopDsl(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "dsl2: after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.stopDsl2(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "dsl3: after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.stopDsl3(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl2: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl2(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl3: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl3(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl4: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl4(fakeRequest)
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
      val result = controller.stop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl: after POST /stop stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.stopDsl(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl2: after POST /stop stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.stopDsl2(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl3: after POST /stop stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.stopDsl3(fakeRequest)
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

    "dsl1: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      an[TimeoutException] shouldBe thrownBy {
        await(controller.wait1(fakeRequest))
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

    "dsl11: after GET /wait show 400 when timeout" in {
      journeyState.set(State.Start, Nil)
      an[TimeoutException] shouldBe thrownBy {
        await(controller.wait11(fakeRequest))
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

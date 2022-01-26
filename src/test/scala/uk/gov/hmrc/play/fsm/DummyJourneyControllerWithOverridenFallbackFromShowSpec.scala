/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.actor.Scheduler
import akka.stream.Materializer
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

class DummyJourneyControllerWithOverridenFallbackFromShowSpec
    extends UnitSpec
    with OneAppPerSuite
    with StateAndBreadcrumbsMatchers {

  import scala.concurrent.duration._

  implicit override val defaultTimeout: FiniteDuration = 360 seconds

  implicit val scheduler: Scheduler = app.actorSystem.scheduler

  implicit lazy val materializer: Materializer = app.materializer

  override lazy val app: Application =
    new GuiceApplicationBuilder().build()

  lazy val journeyState: DummyJourneyService =
    app.injector.instanceOf[DummyJourneyService]

  lazy val controller: DummyJourneyControllerWithOverridenFallbackFromShow =
    app.injector.instanceOf[DummyJourneyControllerWithOverridenFallbackFromShow]

  import journeyState.model.State

  def fakeRequest = FakeRequest()

  "DummyJourneyControllerWithOverridenFallbackFromShow" should {

    "in state Start render a page after showStart" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStart(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "in state Continue render an error after showStart" in {
      journeyState.set(State.Continue("foo"), List(State.Start))
      val result = controller.showStart(fakeRequest)
      status(result) shouldBe 404
      journeyState.get should have[State](State.Continue("foo"), List(State.Start))
    }

    "in state Continue go to Stop after stopDsl12" in {
      journeyState.set(State.Continue("foo"), List(State.Start))
      val result = controller.stopDsl12(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Stop("foo"),
        List(State.Continue("foo"), State.Start)
      )
    }
  }

}

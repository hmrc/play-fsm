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
import DummyJourneyModel.{State, Transition, TransitionNotAllowed, Transitions}

import scala.concurrent.ExecutionContext.Implicits.global

class DummyJourneyModelSpec extends UnitSpec with StateMatchers[State] {

  implicit val context: DummyContext = DummyContext()

  case class given(initialState: State) extends DummyJourneyService {
    Option(initialState) match {
      case Some(state) => await(save((state, Nil)))
      case None        => ()
    }

    def withBreadcrumbs(breadcrumbs: State*): this.type = {
      await(for {
        Some((s, _)) <- fetch
        _            <- save((s, breadcrumbs.toList))
      } yield ())
      this
    }

    def when(transition: Transition): (State, List[State]) =
      await(super.apply(transition).recover { case TransitionNotAllowed(s, b, _) => (s, b) })
  }

  "DummyJourneyModel" when {
    "in an undefined state" should {
      "go to the Start state after `start` transition" in {
        given(null) when Transitions.start should thenGo(State.Start)
      }
      "go to the Continue state after `continue` transition" in {
        given(null) when Transitions.continue(5)("foo") should thenGo(State.Continue("foo"))
      }
      "go to the Continue state after `stop` transition" in {
        given(null) when Transitions.stop(5) should thenGo(State.Stop(""))
      }
    }

    "in a Start state" should {
      "stay in the Start state after `start` transition" in {
        given(State.Start) when Transitions.start should thenGo(State.Start)
      }
      "go to the Continue state after `continue` transition" in {
        given(State.Start) when Transitions.continue(5)("foo") should thenGo(State.Continue("foo"))
      }
      "go to the Continue state after `stop` transition" in {
        given(State.Start) when Transitions.stop(5) should thenGo(State.Stop(""))
      }
    }

    "in a Continue state" should {
      "return to the Start state after `start` transition if Start is in breadcrumbs" in {
        given(State.Continue("dummy"))
          .withBreadcrumbs(State.Start) when Transitions.start should thenGo(State.Start)
      }
      "return to the Start state after `start` transition if Start not in breadcrumbs" in {
        given(State.Continue("dummy")) when Transitions.start should thenGo(State.Start)
      }
      "go to the Continue state after `continue` transition" in {
        given(State.Continue("dummy")) when Transitions.continue(5)("foo") should thenGo(
          State.Continue("dummy,foo"))
      }
      "go to the Continue state after `stop` transition" in {
        given(State.Continue("dummy")) when Transitions.stop(5) should thenGo(State.Stop("dummy"))
      }
    }

    "in a Stop state" should {
      "return to the Start state after `start` transition if Start is in breadcrumbs" in {
        given(State.Stop("foo"))
          .withBreadcrumbs(State.Continue("dummy"), State.Start) when Transitions.start should thenGo(
          State.Start)
      }
      "return to the Start state after `start` transition if Start not in breadcrumbs" in {
        given(State.Stop("foo"))
          .withBreadcrumbs(State.Continue("dummy")) when Transitions.start should thenGo(
          State.Start)
      }
      "stay in the Stop state after `continue` transition" in {
        given(State.Stop("foo")) when Transitions.continue(5)("dummy") should thenGo(
          State.Stop("foo"))
      }
      "throw Exception after `stop` transition" in {
        given(State.Stop("foo")) when Transitions.stop(5) should thenGo(State.Stop("foo"))
      }
    }
  }

}

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
import DummyJourneyModel.{Merger, Mergers, State, Transition, TransitionNotAllowed, Transitions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.util.Try

class DummyJourneyModelSpec extends UnitSpec with StateMatchers[State] {

  implicit val context: DummyContext = DummyContext()

  case class given[S <: State: ClassTag](initialState: S) extends DummyJourneyService {
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

    def shouldFailWhen(transition: Transition) =
      Try(await(super.apply(transition))).isSuccess shouldBe false

    def when(merger: Merger[S], state: State): (State, List[State]) =
      await(super.modify { s: S => merger.apply((s, state)) })
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
      "go to the Continue state after `start then continue` transition" in {
        given(null) when Transitions.start.andThen(Transitions.continue(0)("baz")) should thenGo(
          State.Continue("baz")
        )
      }
      "go to the Stop state after `start then continue then stop` transition" in {
        given(null) when Transitions.start
          .andThen(Transitions.continue(0)("baz"))
          .andThen(Transitions.stop(0)) should thenGo(
          State.Stop("baz")
        )
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
          State.Continue("dummy,foo")
        )
      }
      "go to the Continue state after `stop` transition" in {
        given(State.Continue("dummy")) when Transitions.stop(5) should thenGo(State.Stop("dummy"))
      }
      "copy value from Stop" in {
        given(State.Continue("dummy")) when (Mergers.toContinue, State.Stop("dummy")) should thenGo(
          State.Continue("dummy_dummy")
        )
      }
      "go to the Continue state after `start or continue` transition" in {
        given(State.Start) when Transitions
          .continue(0)("bar")
          .orElse(
            Transitions
              .stop(1)
          ) should thenGo(State.Continue("bar"))
      }
    }

    "in a Stop state" should {
      "return to the Start state after `start` transition if Start is in breadcrumbs" in {
        given(State.Stop("foo"))
          .withBreadcrumbs(
            State.Continue("dummy"),
            State.Start
          ) when Transitions.start should thenGo(State.Start)
      }
      "return to the Start state after `start` transition if Start not in breadcrumbs" in {
        given(State.Stop("foo"))
          .withBreadcrumbs(State.Continue("dummy")) when Transitions.start should thenGo(
          State.Start
        )
      }
      "stay in the Stop state after `continue` transition" in {
        given(State.Stop("foo")) when Transitions.continue(5)("dummy") should thenGo(
          State.Stop("foo")
        )
      }
      "throw Exception after `stop` transition" in {
        given(State.Stop("foo")) when Transitions.stop(5) should thenGo(State.Stop("foo"))
      }
    }
  }

}

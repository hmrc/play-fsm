/*
 * Copyright 2023 HM Revenue & Customs
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

object DummyJourneyModel extends JourneyModel {

  sealed trait State

  object State {
    case object Start extends State
    case class DeadEnd(msg: String) extends State
    case class Continue(arg: String) extends State
    case class Stop(result: String) extends State
  }

  override val root: State = State.Start

  object Transitions {
    import State._

    val start = DummyJourneyModel.start

    val toStart =
      Transition {
        case _ => goto(Start)
      }

    def showContinue(user: Int) =
      Transition {
        case Start      => goto(Continue("yummy"))
        case Stop(curr) => goto(Continue(curr.reverse))
      }

    def doNothing =
      Transition {
        case _ => stay
      }

    def continue(user: Int)(arg: String) =
      Transition {
        case Start          => goto(Continue(arg))
        case Continue(curr) => goto(Continue(curr + "," + arg))
      }

    def stop(user: Int) =
      Transition {
        case Start          => goto(Stop(""))
        case Continue(curr) => goto(Stop(curr))
      }

    def toDeadEnd(fx: String => String) =
      Transition {
        case State.Continue("stop") => goto(State.Stop(fx("continue")))
        case _                      => goto(DeadEnd(fx("empty")))
      }

    def processPayload(payload: TestPayload) =
      Transition {
        case _ => goto(State.Continue(payload.msg))
      }
  }

  object Mergers {

    def toStart =
      Merger[State.Start.type] {
        case (state, State.Stop(curr)) => State.Start
      }

    def toContinue =
      Merger[State.Continue] {
        case (state, State.Stop(curr)) => state.copy(arg = curr + "_" + curr)
      }

    def toDeadEnd =
      Merger[State.DeadEnd] {
        case (state, State.Continue(curr)) => State.DeadEnd(curr)
      }

  }

}

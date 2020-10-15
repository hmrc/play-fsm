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

object DummyJourneyModel extends JourneyModel {

  sealed trait State

  object State {
    case object Start extends State
    case class Continue(arg: String) extends State
    case class Stop(result: String) extends State
  }

  override val root: State = State.Start

  object Transitions {
    import State._

    val start = DummyJourneyModel.start

    def showContinue(user: Int) =
      Transition {
        case Stop(curr) => goto(Continue(curr.reverse))
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
  }

  object Merging {

    def toStart =
      Merge[State.Start.type] {
        case (state, State.Stop(curr)) => State.Start
      }

    def toContinue =
      Merge[State.Continue] {
        case (state, State.Stop(curr)) => state.copy(arg = curr + "_" + curr)
      }

  }

}

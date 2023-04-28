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

import DummyJourneyModel.State
import play.api.libs.json._

object DummyJourneyStateFormats extends JsonStateFormats[State] {

  val ContinueFormat = Json.format[State.Continue]
  val StopFormat     = Json.format[State.Stop]
  val DeadEndFormat  = Json.format[State.DeadEnd]

  override val serializeStateProperties: PartialFunction[State, JsValue] = {
    case s: State.Continue => ContinueFormat.writes(s)
    case s: State.Stop     => StopFormat.writes(s)
    case s: State.DeadEnd  => DeadEndFormat.writes(s)
  }
  override def deserializeState(stateName: String, properties: JsValue): JsResult[State] =
    stateName match {
      case "Start"    => JsSuccess(State.Start)
      case "Continue" => ContinueFormat.reads(properties)
      case "Stop"     => StopFormat.reads(properties)
      case "DeadEnd"  => DeadEndFormat.reads(properties)
      case _          => JsError(s"Unknown state name $stateName")
    }
}

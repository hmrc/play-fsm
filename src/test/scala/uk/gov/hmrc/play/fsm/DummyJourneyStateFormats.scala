package uk.gov.hmrc.play.fsm

import DummyJourneyModel.State
import play.api.libs.json._

object DummyJourneyStateFormats extends JsonStateFormats[State] {

  val ContinueFormat = Json.format[State.Continue]
  val StopFormat     = Json.format[State.Stop]

  override val serializeStateProperties: PartialFunction[State, JsValue] = {
    case s: State.Continue => ContinueFormat.writes(s)
    case s: State.Stop     => StopFormat.writes(s)
  }
  override def deserializeState(stateName: String, properties: JsValue): JsResult[State] = stateName match {
    case "Start"    => JsSuccess(State.Start)
    case "Continue" => ContinueFormat.reads(properties)
    case "Stop"     => StopFormat.reads(properties)
    case _          => JsError(s"Unknown state name $stateName")
  }
}

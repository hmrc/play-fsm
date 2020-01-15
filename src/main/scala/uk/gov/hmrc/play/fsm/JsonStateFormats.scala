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

import play.api.libs.json._

/**
  * Utility to generate Play JSON Format for multi-value State
  * @tparam State state values base type
  */
trait JsonStateFormats[State] {

  val serializeStateProperties: PartialFunction[State, JsValue]
  def deserializeState(stateName: String, properties: JsValue): JsResult[State]

  final val reads: Reads[State] = new Reads[State] {
    override def reads(json: JsValue): JsResult[State] = json match {
      case obj: JsObject =>
        (obj \ "state")
          .asOpt[String]
          .map(s => (obj \ "properties").asOpt[JsValue].map(p => (s, p)).getOrElse((s, JsNull))) match {
          case Some((stateName, properties)) => deserializeState(stateName, properties)
          case None                          => JsError("Missing state field")
        }

      case o => JsError(s"Cannot parse State from $o, must be JsObject.")
    }
  }

  final val writes: Writes[State] = new Writes[State] {
    override def writes(state: State): JsValue =
      if (serializeStateProperties.isDefinedAt(state)) serializeStateProperties(state) match {
        case JsNull     => Json.obj("state" -> nameOf(state))
        case properties => Json.obj("state" -> nameOf(state), "properties" -> properties)
      } else Json.obj("state" -> nameOf(state))
  }

  final def formats: Format[State] = Format(reads, writes)

  final def nameOf(state: State): String = {
    val className = state.getClass.getName
    val lastDot   = className.lastIndexOf('.')
    val typeName = {
      val s = if (lastDot < 0) className else className.substring(lastDot + 1)
      if (s.last == '$') s.init else s
    }
    val lastDollar = typeName.lastIndexOf('$')
    if (lastDollar < 0) typeName else typeName.substring(lastDollar + 1)
  }

}

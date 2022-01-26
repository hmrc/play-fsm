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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.play.fsm.DummyJourneyModel.State

class DummyJourneyStateFormatsSpec extends UnitSpec {

  implicit val formats: Format[State] = DummyJourneyStateFormats.formats

  "DummyJourneyStateFormats" should {
    "serialize and deserialize state" when {

      "Start" in {
        Json.toJson[State](State.Start) shouldBe Json
          .obj("state" -> "Start")
        Json
          .parse("""{"state":"Start"}""")
          .as[State] shouldBe State.Start
      }

      "Continue" in {
        Json.toJson[State](State.Continue("dummy")) shouldBe Json
          .obj("state" -> "Continue", "properties" -> Json.obj("arg" -> "dummy"))
        Json
          .parse("""{"state":"Continue", "properties":{"arg":"dummy"}}""")
          .as[State] shouldBe State.Continue("dummy")
      }

      "Stop" in {
        Json.toJson[State](State.Stop("foo")) shouldBe Json
          .obj("state" -> "Stop", "properties" -> Json.obj("result" -> "foo"))
        Json
          .parse("""{"state":"Stop","properties":{"result":"foo"}}""")
          .as[State] shouldBe State.Stop("foo")
      }

    }

  }

}

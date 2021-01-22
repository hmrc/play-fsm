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

import play.api.data.Form
import play.api.data.Forms.{number, single}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest

class OptionalFormOpsSpec extends UnitSpec {

  val form          = Form(single("arg" -> number))
  val formWithError = form.withError("arg", "foo")

  import OptionalFormOps._

  "OptionalFormOps.or" should {
    "return the first arg over the second arg if Some" in {
      implicit val request: Request[AnyContent] = FakeRequest()
      Some(formWithError).or(form) shouldBe formWithError
    }

    "retrieve form bound from request if first arg is None and flash cookie is present" in {
      implicit val request: Request[AnyContent] = FakeRequest().withFlash("arg" -> "xyz")
      None.or(form).errors shouldBe form.bind(Map("arg" -> "xyz")).errors
    }

    "retrieve form bound from request if first arg is None and empty flash cookie is present" in {
      implicit val request: Request[AnyContent] = FakeRequest().withFlash("dummy" -> "")
      None.or(form).errors shouldBe form.bind(Map.empty[String, String]).errors
    }

    "return the second arg if first arg is None and request flash cookie is Empty" in {
      implicit val request: Request[AnyContent] = FakeRequest()
      None.or(form) shouldBe form
    }
  }
}

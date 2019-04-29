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

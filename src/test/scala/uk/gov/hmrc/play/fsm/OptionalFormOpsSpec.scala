package uk.gov.hmrc.play.fsm

import play.api.data.Form
import play.api.data.Forms.{single, text}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

class OptionalFormOpsSpec extends UnitSpec {

  val form          = Form(single("arg" -> text))
  val formWithError = form.withError("arg", "foo")

  import OptionalFormOps._

  "OptionalFormOps.or" should {
    "return the first arg over the second arg if Some" in {
      implicit val request: Request[AnyContent] = FakeRequest()
      Some(formWithError).or(form) shouldBe formWithError
    }

    "retrieve form bound from request if first arg is None and flash cookie is present" in {
      implicit val request: Request[AnyContent] = FakeRequest().withFlash("arg" -> "xyz")
      None.or(form) shouldBe form.bind(Map("arg" -> "xyz"))
    }

    "retrieve form bound from request if first arg is None and empty flash cookie is present" in {
      implicit val request: Request[AnyContent] = FakeRequest().withFlash()
      None.or(form) shouldBe form.bind(Map.empty[String, String])
    }

    "return the second arg if first arg is None and request flash cookie is Empty" in {
      implicit val request: Request[AnyContent] = FakeRequest()
      None.or(form) shouldBe form
    }
  }
}

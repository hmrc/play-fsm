package uk.gov.hmrc.play.fsm
import play.api.data.Form
import play.api.mvc.{Flash, Request}

object OptionalFormOps {
  implicit class OptionalForm(val formOpt: Option[Form[_]]) extends AnyVal {
    def or[T](other: Form[T])(implicit request: Request[_]): Form[T] =
      formOpt
        .map(_.asInstanceOf[Form[T]])
        .getOrElse({
          if (request.cookies.get(Flash.COOKIE_NAME).isDefined) other.bind(request.flash.data) else other
        })
  }
}

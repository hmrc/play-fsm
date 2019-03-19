package uk.gov.hmrc.play.fsm
import play.api.data.Form

object OptionalFormOps {
  implicit class OptionalForm(val formOpt: Option[Form[_]]) extends AnyVal {
    def or[T](other: Form[T]): Form[T] = formOpt.map(_.asInstanceOf[Form[T]]).getOrElse(other)
  }
}

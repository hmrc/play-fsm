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

import play.api.data.Form
import play.api.mvc.{Flash, Request}

object OptionalFormOps {
  implicit class OptionalForm(val formWithErrors: Option[Form[_]]) extends AnyVal {

    /** Returns formWithErrors or an empty form. */
    def or[T](emptyForm: Form[T])(implicit request: Request[_]): Form[T] =
      formWithErrors
        .map(_.asInstanceOf[Form[T]])
        .getOrElse {
          if (request.flash.isEmpty) emptyForm else emptyForm.bind(request.flash.data)
        }

    /** Returns formWithErrors or a pre-filled form, or an empty form. */
    def or[T](emptyForm: Form[T], maybeFillWith: Option[T])(implicit request: Request[_]): Form[T] =
      formWithErrors
        .map(_.asInstanceOf[Form[T]])
        .getOrElse {
          if (request.flash.isEmpty) maybeFillWith.map(emptyForm.fill).getOrElse(emptyForm)
          else emptyForm.bind(request.flash.data)
        }
  }
}

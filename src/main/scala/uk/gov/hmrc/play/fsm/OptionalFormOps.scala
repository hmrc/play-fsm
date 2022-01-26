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

import play.api.data.Form
import play.api.mvc.Request
import play.api.mvc.Flash

/** Extension methods for Option[Form[_]]. */
object OptionalFormOps {

  final val knownUnrelatedFlashKeys: Set[String] = Set("switching-language")

  private def getFormData(flash: Flash, unrelatedFlashKeys: Set[String]): Map[String, String] =
    flash.data.filterNot { case (key, _) => unrelatedFlashKeys.contains(key) }

  implicit class OptionalForm(val formWithErrors: Option[Form[_]]) extends AnyVal {

    /** Returns formWithErrors or a pre-filled form, or an empty form. */
    final def or[T](
      emptyForm: Form[T],
      maybeFillWith: Option[T] = None,
      unrelatedFormKeys: Set[String] = knownUnrelatedFlashKeys
    )(implicit request: Request[_]): Form[T] =
      formWithErrors
        .map(_.asInstanceOf[Form[T]])
        .getOrElse {
          val formData = getFormData(request.flash, unrelatedFormKeys)
          if (formData.isEmpty) maybeFillWith.map(emptyForm.fill).getOrElse(emptyForm)
          else emptyForm.bind(formData)
        }
  }
}

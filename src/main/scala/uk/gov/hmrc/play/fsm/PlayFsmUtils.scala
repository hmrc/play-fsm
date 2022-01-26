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

import scala.annotation.tailrec

object PlayFsmUtils {

  final def identityOf[A](entity: A): String = {

    @tailrec
    def simpleNameOf(clazz: Class[_], suffix: String): String = {
      val name = clazz.getName()
      val id   = normalize(name)
      if (name.contains("$anon$"))
        simpleNameOf(entity.getClass().getSuperclass(), combine(id, suffix))
      else
        combine(id, suffix)
    }

    simpleNameOf(entity.getClass(), "")
  }

  final def normalize(name: String): String = {
    val name1 =
      if (name.endsWith("$") || name.endsWith(".")) name.dropRight(1)
      else name
    val name2 = {
      val i = Math.max(name1.lastIndexOf("."), name1.lastIndexOf("$")) + 1
      name1.substring(i)
    }
    name2
  }

  @inline final def combine(a: String, b: String): String =
    if (a.isEmpty())
      b
    else if (b.isEmpty())
      a
    else
      s"$a:$b"

}

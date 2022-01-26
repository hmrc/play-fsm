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

class PlayFsmUtilsSpec extends UnitSpec {

  class A
  case class B1()
  trait Cx
  object Dd {
    class E {
      trait Gaga extends Cx
      trait Ii extends A
    }
    trait Foo
    case class Ha() extends Foo with Cx
    case object Jay
    case object Key {
      case class EL()
    }
  }

  "PlayFsmUtils" should {
    "normalize a name" in {
      PlayFsmUtils.normalize("")          shouldBe ""
      PlayFsmUtils.normalize(".")         shouldBe ""
      PlayFsmUtils.normalize("..")        shouldBe ""
      PlayFsmUtils.normalize("A.")        shouldBe "A"
      PlayFsmUtils.normalize("a.ABC.")    shouldBe "ABC"
      PlayFsmUtils.normalize("a.b.$Bcd$") shouldBe "Bcd"
      PlayFsmUtils.normalize("a.b.$Bcd")  shouldBe "Bcd"
      PlayFsmUtils.normalize("a.b.Bcd")   shouldBe "Bcd"
    }

    "return an identity of a state type" in {
      PlayFsmUtils.identityOf(new A)         shouldBe "A"
      PlayFsmUtils.identityOf(B1())          shouldBe "B1"
      PlayFsmUtils.identityOf(new Cx {})     shouldBe "Object:1"
      PlayFsmUtils.identityOf(Dd)            shouldBe "Dd"
      PlayFsmUtils.identityOf(new A {})      shouldBe "A:2"
      PlayFsmUtils.identityOf(new Dd.E)      shouldBe "E"
      PlayFsmUtils.identityOf(new Dd.Foo {}) shouldBe "Object:3"
      PlayFsmUtils.identityOf(Dd.Ha())       shouldBe "Ha"
      PlayFsmUtils.identityOf { val e = new Dd.E; new e.Gaga {} } shouldBe "Object:4"
      PlayFsmUtils.identityOf { val e = new Dd.E; new e.Ii {} } shouldBe "A:5"
      PlayFsmUtils.identityOf(Dd.Jay)      shouldBe "Jay"
      PlayFsmUtils.identityOf(Dd.Key)      shouldBe "Key"
      PlayFsmUtils.identityOf(Dd.Key.EL()) shouldBe "EL"
    }

  }
}

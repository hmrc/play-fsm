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

import scala.concurrent.ExecutionContext.Implicits.global

class JourneyServiceSpec extends UnitSpec {

  implicit val context = DummyContext()

  val testService = new TestJourneyService {
    override val model =
      new TestJourneyModel

    override val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs =
      _.take(9)
  }

  "PersistentJourneyService" should {
    "apply transition and return new state keeping account of the breadcrumbs" in {
      await(testService.save(("foo", Nil)))
      await(testService.apply(testService.model.Transitions.append("bar")))
      await(testService.fetch) shouldBe Some(("foobar", List("foo")))
      await(testService.apply(testService.model.Transitions.reverse))
      await(testService.fetch) shouldBe Some(("raboof", List("foobar", "foo")))
      await(testService.apply(testService.model.Transitions.append("bar")))
      await(testService.fetch) shouldBe Some(("raboofbar", List("raboof", "foobar", "foo")))
      await(testService.apply(testService.model.Transitions.reverse))
      await(testService.fetch) shouldBe Some(
        ("rabfoobar", List("raboofbar", "raboof", "foobar", "foo"))
      )
      await(testService.apply(testService.model.Transitions.append("foo")))
      await(testService.fetch) shouldBe Some(
        ("rabfoobarfoo", List("rabfoobar", "raboofbar", "raboof", "foobar", "foo"))
      )
      await(testService.apply(testService.model.Transitions.reverse))
      await(testService.fetch) shouldBe Some(
        ("oofraboofbar", List("rabfoobarfoo", "rabfoobar", "raboofbar", "raboof", "foobar", "foo"))
      )
      await(testService.apply(testService.model.Transitions.replace("o", "x")))
      await(testService.fetch) shouldBe Some(
        (
          "xxfrabxxfbar",
          List("oofraboofbar", "rabfoobarfoo", "rabfoobar", "raboofbar", "raboof", "foobar", "foo")
        )
      )
      await(testService.apply(testService.model.Transitions.replace("xx", "o")))
      await(testService.fetch) shouldBe Some(
        (
          "ofrabofbar",
          List(
            "xxfrabxxfbar",
            "oofraboofbar",
            "rabfoobarfoo",
            "rabfoobar",
            "raboofbar",
            "raboof",
            "foobar",
            "foo"
          )
        )
      )
      await(testService.apply(testService.model.Transitions.reverse))
      await(testService.fetch) shouldBe Some(
        (
          "rabfobarfo",
          List(
            "ofrabofbar",
            "xxfrabxxfbar",
            "oofraboofbar",
            "rabfoobarfoo",
            "rabfoobar",
            "raboofbar",
            "raboof",
            "foobar",
            "foo"
          )
        )
      )
      await(testService.apply(testService.model.Transitions.append("bar")))
      await(testService.fetch) shouldBe Some(
        (
          "rabfobarfobar",
          List(
            "rabfobarfo",
            "ofrabofbar",
            "xxfrabxxfbar",
            "oofraboofbar",
            "rabfoobarfoo",
            "rabfoobar",
            "raboofbar",
            "raboof",
            "foobar",
            "foo"
          )
        )
      )
      await(testService.apply(testService.model.Transitions.reverse))
      await(testService.fetch) shouldBe Some(
        (
          "rabofrabofbar",
          List(
            "rabfobarfobar",
            "rabfobarfo",
            "ofrabofbar",
            "xxfrabxxfbar",
            "oofraboofbar",
            "rabfoobarfoo",
            "rabfoobar",
            "raboofbar",
            "raboof",
            "foobar"
          )
        )
      ) // foo is removed because we keep only 10 last states
    }
  }

  "return current state and breadcrumbs" in {
    await(testService.save(("foo", Nil)))
    await(testService.save(("bar", List("foo"))))
    await(testService.save(("zoo", List("bar", "foo"))))
    await(testService.fetch) shouldBe Some(("zoo", List("bar", "foo")))
  }

  "step back and return previous state and breadcrumbs" in {
    await(testService.save(("foo", Nil)))
    await(testService.save(("bar", List("foo"))))
    await(testService.fetch)    shouldBe Some(("bar", List("foo")))
    await(testService.stepBack) shouldBe Some(("foo", Nil))
    await(testService.stepBack) shouldBe None
  }

  "clean breadcrumbs" in {
    await(testService.save(("foo", Nil)))
    await(testService.save(("bar", List("foo"))))
    await(testService.fetch)              shouldBe Some(("bar", List("foo")))
    await(testService.cleanBreadcrumbs()) shouldBe List("foo")
    await(testService.fetch)              shouldBe Some(("bar", Nil))
    await(testService.cleanBreadcrumbs()) shouldBe Nil
    await(testService.fetch)              shouldBe Some(("bar", Nil))
  }

}

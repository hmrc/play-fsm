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

import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Try

trait JourneyModelSpec extends TestJourneyService with JourneyMatchers {
  self: Matchers =>

  val model: JourneyModel

  case class given[S <: model.State: ClassTag](initialState: S) {

    import scala.concurrent.ExecutionContext.Implicits.global

    private def await[A](future: Future[A])(implicit timeout: Duration): A =
      Await.result(future, timeout)

    implicit val context: DummyContext          = DummyContext()
    implicit val defaultTimeout: FiniteDuration = 5 seconds

    Option(initialState) match {
      case Some(state) => await(save((state, Nil)))
      case None        => await(clear)
    }

    def withBreadcrumbs(breadcrumbs: model.State*): this.type = {
      await(for {
        Some((s, _)) <- fetch
        _            <- save((s, breadcrumbs.toList))
      } yield ())
      this
    }

    def when(transition: model.Transition): (model.State, List[model.State]) =
      await(apply(transition).recover { case model.TransitionNotAllowed(s, b, _) => (s, b) })

    def shouldFailWhen(transition: model.Transition) =
      Try(await(apply(transition))).isSuccess shouldBe false

    def when(merger: model.Merger[S], state: model.State): (model.State, List[model.State]) =
      await(modify { s: S => merger.apply((s, state)) })
  }

}

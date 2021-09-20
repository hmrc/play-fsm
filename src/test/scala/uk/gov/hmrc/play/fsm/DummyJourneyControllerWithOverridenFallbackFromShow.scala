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
import akka.actor.ActorSystem
import play.api.mvc._

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class DummyJourneyControllerWithOverridenFallbackFromShow @Inject() (
  a: DummyJourneyService,
  b: DefaultActionBuilder,
  c: ControllerComponents
)(implicit
  ec: ExecutionContext,
  actorSystem: ActorSystem
) extends DummyJourneyController(a, b, c) {

  override lazy val defaultFallbackFromShow: Fallback =
    (requestContext: DummyContext, request: Request[_], executionContext: ExecutionContext) =>
      Future.successful(NotFound)

}

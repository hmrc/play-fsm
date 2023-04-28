/*
 * Copyright 2023 HM Revenue & Customs
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

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import java.util.concurrent.atomic.AtomicInteger

trait TestJourneyService[RequestContext] extends PersistentJourneyService[RequestContext] {

  override val journeyKey: String = "TestJourney"

  private val counter: AtomicInteger = new AtomicInteger(0)

  val storage = new InMemoryStore[(model.State, List[model.State])] {}

  override def apply(
    transition: model.Transition
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs] = {
    counter.incrementAndGet()
    super.apply(transition)(rc, ec)
  }

  override def fetch(implicit
    hc: RequestContext,
    ec: ExecutionContext
  ): Future[Option[(model.State, List[model.State])]] = storage.fetch

  override def save(
    state: (model.State, List[model.State])
  )(implicit hc: RequestContext, ec: ExecutionContext): Future[(model.State, List[model.State])] =
    storage.save(state)

  def set(state: model.State, breadcrumbs: List[model.State])(implicit
    headerCarrier: RequestContext,
    timeout: Duration,
    ec: ExecutionContext
  ): Unit =
    Await.result(save((state, breadcrumbs)), timeout)

  def get(implicit
    headerCarrier: RequestContext,
    timeout: Duration,
    ec: ExecutionContext
  ): Option[StateAndBreadcrumbs] =
    Await.result(fetch, timeout)

  override def clear(): Future[Unit] =
    Future.successful(storage.clear())

  def getCounter(): Int = counter.get()

}

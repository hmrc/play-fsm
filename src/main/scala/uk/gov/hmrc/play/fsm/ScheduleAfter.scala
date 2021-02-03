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

import akka.pattern.FutureTimeoutSupport
import akka.actor.Scheduler
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

object ScheduleAfter extends FutureTimeoutSupport {

  /** Delay execution of the future by given miliseconds */
  def apply[T](
    delayInMiliseconds: Long
  )(body: => Future[T])(implicit scheduler: Scheduler, ec: ExecutionContext): Future[T] =
    after(duration = FiniteDuration(delayInMiliseconds, "ms"), using = scheduler)(body)
}

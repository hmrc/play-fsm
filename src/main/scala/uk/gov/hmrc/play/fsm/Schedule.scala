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

import java.util.{Timer, TimerTask}
import java.util.Date
import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.Success
import scala.util.Failure

object Schedule {

  def apply[T](delay: Long)(body: => Future[T])(implicit ctx: ExecutionContext): Future[T] =
    makeTask(body)(timer.schedule(_, delay))

  def apply[T](date: Date)(body: => Future[T])(implicit ctx: ExecutionContext): Future[T] =
    makeTask(body)(timer.schedule(_, date))

  def apply[T](
    delay: FiniteDuration
  )(body: => Future[T])(implicit ctx: ExecutionContext): Future[T] =
    makeTask(body)(timer.schedule(_, delay.toMillis))

  private val timer = new Timer(true)

  private def makeTask[T](
    body: => Future[T]
  )(schedule: TimerTask => Unit)(implicit ctx: ExecutionContext): Future[T] = {
    val prom = Promise[T]()
    schedule(
      new TimerTask {
        def run() {
          // IMPORTANT: The timer task just starts the execution on the passed
          // ExecutionContext and is thus almost instantaneous (making it
          // practical to use a single  Timer - hence a single background thread).
          body.transform(
            { result: T =>
              prom.complete(Success(result))
              result
            },
            { error: Throwable =>
              prom.complete(Failure(error))
              error
            }
          )
        }
      }
    )
    prom.future
  }
}

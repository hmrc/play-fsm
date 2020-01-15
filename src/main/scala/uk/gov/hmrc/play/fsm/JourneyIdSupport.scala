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

import java.util.UUID

import play.api.mvc.{Request, RequestHeader, Result, Results}

import scala.concurrent.{ExecutionContext, Future}

trait JourneyIdSupport[RequestContext] {
  self: JourneyController[RequestContext] =>

  def amendContext(rc: RequestContext)(key: String, value: String): RequestContext

  def journeyId(implicit rh: RequestHeader): Option[String] =
    rh.session.get(journeyService.journeyKey)

  def appendJourneyId(rc: RequestContext)(implicit rh: RequestHeader): RequestContext =
    journeyId.map(value => amendContext(rc)(journeyService.journeyKey, value)).getOrElse(rc)

  def appendJourneyId(result: Result)(implicit rh: RequestHeader): Result = {
    val journeyKeyValue = journeyService.journeyKey -> journeyId(rh).getOrElse(
      UUID.randomUUID().toString)
    result.withSession(result.session + journeyKeyValue)
  }

  override def withValidRequest(body: => Future[Result])(
    implicit rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext): Future[Result] =
    journeyId match {
      case None =>
        journeyService.initialState.map { state =>
          appendJourneyId(Results.Redirect(getCallFor(state)))(request)
        }
      case _ => body
    }

}

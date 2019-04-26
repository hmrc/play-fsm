package uk.gov.hmrc.play.fsm

import java.util.UUID

import play.api.mvc.{Request, RequestHeader, Result, Results}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait JourneyIdSupport {
  self: JourneyController =>

  def journeyId(implicit rh: RequestHeader): Option[String] = rh.session.get(journeyService.journeyKey)

  def appendJourneyId(hc: HeaderCarrier)(implicit rh: RequestHeader): HeaderCarrier =
    journeyId.map(value => hc.withExtraHeaders(journeyService.journeyKey -> value)).getOrElse(hc)

  def appendJourneyId(result: Result)(implicit rh: RequestHeader): Result =
    result.withSession(journeyService.journeyKey -> journeyId(rh).getOrElse(UUID.randomUUID().toString))

  override def withValidRequest(
    body: => Future[Result])(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Future[Result] =
    journeyId match {
      case None =>
        journeyService.initialState.map { state =>
          appendJourneyId(Results.Redirect(getCallFor(state)))(request)
        }
      case _ => body
    }

}

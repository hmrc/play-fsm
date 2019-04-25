package uk.gov.hmrc.play.fsm

import java.util.UUID

import play.api.mvc.{Request, RequestHeader, Result, Results}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait JourneyIdSupport {
  self: JourneyController =>

  def journeyId(implicit rh: RequestHeader): Option[String] = rh.session.get(journeyService.journeyKey)

  def appendJourneyId(hc: HeaderCarrier)(implicit rh: RequestHeader): HeaderCarrier =
    journeyId.map(value => hc.withExtraHeaders(journeyService.journeyKey -> value)).getOrElse(hc)

  def appendJourneyId(result: Result)(implicit rh: RequestHeader): Result =
    result.withSession(journeyService.journeyKey -> journeyId(rh).getOrElse(UUID.randomUUID().toString))

  override def withValidRequest(body: => Future[Result])(implicit request: Request[_]): Future[Result] =
    journeyId match {
      case None =>
        Future.successful(appendJourneyId(Results.Redirect(getCallFor(journeyService.model.root)))(request))
      case _ => body
    }

}

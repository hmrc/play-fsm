package uk.gov.hmrc.play.fsm

import java.util.UUID

import play.api.mvc.{Request, RequestHeader, Result, Results}

import scala.concurrent.{ExecutionContext, Future}

trait JourneyIdSupport[RequestContext] {
  self: JourneyController[RequestContext] =>

  def amendContext(rc: RequestContext)(key: String, value: String): RequestContext

  def journeyId(implicit rh: RequestHeader): Option[String] = rh.session.get(journeyService.journeyKey)

  def appendJourneyId(rc: RequestContext)(implicit rh: RequestHeader): RequestContext =
    journeyId.map(value => amendContext(rc)(journeyService.journeyKey, value)).getOrElse(rc)

  def appendJourneyId(result: Result)(implicit rh: RequestHeader): Result =
    result.withSession(journeyService.journeyKey -> journeyId(rh).getOrElse(UUID.randomUUID().toString))

  override def withValidRequest(
    body: => Future[Result])(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    journeyId match {
      case None =>
        journeyService.initialState.map { state =>
          appendJourneyId(Results.Redirect(getCallFor(state)))(request)
        }
      case _ => body
    }

}

package uk.gov.hmrc.play.fsm

import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.HeaderCarrier

trait HeaderCarrierProvider {

  protected def hc(implicit rh: RequestHeader): HeaderCarrier
}

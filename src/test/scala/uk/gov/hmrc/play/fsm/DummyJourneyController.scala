package uk.gov.hmrc.play.fsm
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{single, text}
import play.api.mvc._
import play.twirl.api.Html
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.fsm.OptionalFormOps._

import scala.concurrent.ExecutionContext

@Singleton
class DummyJourneyController @Inject()(override val journeyService: DummyJourneyService)(implicit ec: ExecutionContext)
    extends Controller
    with JourneyController {

  import DummyJourneyController._
  import journeyService.model.{State, Transitions}

  override implicit def hc(implicit rh: RequestHeader): HeaderCarrier = HeaderCarrier()

  val asUser: WithAuthorised[Int] = { implicit request => body =>
    body(5)
  }

  // ACTIONS

  val start: Action[AnyContent] = action { implicit request =>
    journeyService.cleanBreadcrumbs
      .flatMap(_ => apply(journeyService.model.start, display))
  }

  val showStart: Action[AnyContent] = showCurrentStateWhen {
    case State.Start =>
  }

  def continue: Action[AnyContent] = action { implicit request =>
    authorisedWithForm(asUser)(ArgForm)(Transitions.continue)
  }

  val showContinue: Action[AnyContent] = showCurrentStateWhenAuthorised(asUser) {
    case State.Continue(_) =>
  }

  val stop: Action[AnyContent] = action { implicit request =>
    authorised(asUser)(Transitions.stop)(redirect)
  }

  val showStop: Action[AnyContent] = showCurrentStateWhenAuthorised(asUser) {
    case State.Stop(_) =>
  }

  // VIEWS

  /** implement this to map states into endpoints for redirection and back linking */
  override def getCallFor(state: journeyService.model.State)(implicit request: Request[_]): Call = state match {
    case State.Start       => Call("GET", "/start")
    case State.Continue(_) => Call("GET", "/continue")
    case State.Stop(_)     => Call("GET", "/stop")
  }

  /** implement this to render state after transition or when form validation fails */
  override def renderState(
    state: journeyService.model.State,
    breadcrumbs: List[journeyService.model.State],
    formWithErrors: Option[Form[_]])(implicit request: Request[_]): Result =
    state match {
      case State.Start         => Ok(Html(s"""Start | <a href="${backLinkFor(breadcrumbs).url}">back</a>"""))
      case State.Continue(arg) => Ok(s"Continue with $arg and form ${formWithErrors.or(ArgForm)}")
      case State.Stop(result)  => Ok(s"Result is $result")
    }
}

object DummyJourneyController {

  val ArgForm: Form[String] = Form(
    single(
      "arg" -> text
    )
  )

}

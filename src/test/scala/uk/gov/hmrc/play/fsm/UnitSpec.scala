package uk.gov.hmrc.play.fsm

import java.nio.charset.Charset

import akka.stream.Materializer
import akka.util.ByteString
import org.scalatest.{Matchers, OptionValues, WordSpecLike}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

trait UnitSpec extends WordSpecLike with Matchers with OptionValues {

  import scala.concurrent.duration._
  import scala.concurrent.{Await, Future}

  implicit val defaultTimeout: FiniteDuration = 5 seconds

  implicit def extractAwait[A](future: Future[A]): A = await[A](future)

  def await[A](future: Future[A])(implicit timeout: Duration): A = Await.result(future, timeout)

  // Convenience to avoid having to wrap andThen() parameters in Future.successful
  implicit def liftFuture[A](v: A): Future[A] = Future.successful(v)

  def status(of: Result): Int = of.header.status

  def status(of: Future[Result])(implicit timeout: Duration): Int = status(Await.result(of, timeout))

  def jsonBodyOf(result: Result)(implicit mat: Materializer): JsValue =
    Json.parse(bodyOf(result))

  def jsonBodyOf(resultF: Future[Result])(implicit mat: Materializer): Future[JsValue] =
    resultF.map(jsonBodyOf)

  def bodyOf(result: Result)(implicit mat: Materializer): String = {
    val bodyBytes: ByteString = await(result.body.consumeData)
    // We use the default charset to preserve the behaviour of a previous
    // version of this code, which used new String(Array[Byte]).
    // If the fact that the previous version used the default charset was an
    // accident then it may be better to decode in UTF-8 or the charset
    // specified by the result's headers.
    bodyBytes.decodeString(Charset.defaultCharset().name)
  }

  def bodyOf(resultF: Future[Result])(implicit mat: Materializer): Future[String] =
    resultF.map(bodyOf)
}

/*
 * Copyright 2022 HM Revenue & Customs
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

import org.scalatest.matchers.MatchResult
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.reflect.ClassTag
import org.scalactic.source
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Informing
import scala.io.AnsiColor

/**
  * Abstract base of FSM journey specifications.
  *
  * @example
  *
  *   given(State_A)
  *     .when(transition)
  *     .thenGoes(State_B)
  *
  *   given(State_A)
  *     .when(transition)
  *     .thenMatches {
  *       case State_B(...) =>
  *     }
  *
  *   given(State_A)
  *     .when(transition)
  *     .thenNoChange
  *
  *   given(State_A)
  *     .when(transition)
  *     .thenFailsWith[SomeExceptionType]
  */
trait JourneyModelSpec extends TestJourneyService[DummyContext] {
  self: Matchers with BeforeAndAfterAll with Informing =>

  val model: JourneyModel

  implicit val defaultTimeout: FiniteDuration = 5 seconds
  import scala.concurrent.ExecutionContext.Implicits.global

  def await[A](future: Future[A])(implicit timeout: Duration): A =
    Await.result(future, timeout)

  /** Assumption about the initial state of journey. */
  case class given[S <: model.State: ClassTag](
    initialState: S,
    breadcrumbs: List[model.State] = Nil
  ) {

    final def withBreadcrumbs(breadcrumbs: model.State*): given[S] =
      given(initialState, breadcrumbs.toList)

    final def when(transition: model.Transition): When = {
      Option(initialState) match {
        case Some(state) => await(save((state, breadcrumbs)))
        case None        => await(clear)
      }
      val resultOrException = await(
        apply(transition)
          .map(Right.apply)
          .recover {
            case model.TransitionNotAllowed(s, b, _) => Right((s, b))
            case model.StayInCurrentState            => await(fetch).toRight(model.StayInCurrentState)
            case exception                           => Left(exception)
          }
      )
      When(initialState, resultOrException)
    }

    final def when(merger: model.Merger[S], state: model.State): When = {
      Option(initialState) match {
        case Some(state) => await(save((state, breadcrumbs)))
        case None        => await(clear)
      }
      val resultOrException =
        await(
          modify { s: S => merger.apply((s, state)) }
            .map(Right.apply)
            .recover {
              case model.TransitionNotAllowed(s, b, _) => Right((s, b))
              case model.StayInCurrentState            => await(fetch).toRight(model.StayInCurrentState)
              case exception                           => Left(exception)
            }
        )
      When(initialState, resultOrException)
    }
  }

  /** State transition result. */
  case class When(
    initialState: model.State,
    result: Either[Throwable, (model.State, List[model.State])]
  ) {

    /** Asserts that the resulting state of the transition is equal to some expected state. */
    final def thenGoes(state: model.State)(implicit pos: source.Position): Unit =
      this should JourneyModelSpec.this.thenGo(state)

    /** Asserts that the resulting state of the transition matches some case. */
    final def thenMatches(
      statePF: PartialFunction[model.State, Unit]
    )(implicit pos: source.Position): Unit =
      this should JourneyModelSpec.this.thenMatch(statePF)

    /** Asserts that the transition hasn't change the state. */
    final def thenNoChange(implicit pos: source.Position): Unit =
      this should JourneyModelSpec.this.changeNothing

    /** Asserts that the transition threw some expected exception of type E. */
    final def thenFailsWith[E <: Throwable](implicit ct: ClassTag[E], pos: source.Position): Unit =
      this should JourneyModelSpec.this.failWith[E]

  }

  /** Asserts that the resulting state of the transition is equal to some expected state. */
  final def thenGo(state: model.State): Matcher[When] =
    new Matcher[When] {
      override def apply(result: When): MatchResult =
        result match {
          case When(_, Left(exception)) =>
            MatchResult(false, s"Transition has been expected but got an exception $exception", s"")

          case When(_, Right((thisState, _))) if state != thisState =>
            if (state != result.initialState && thisState == result.initialState)
              MatchResult(
                false,
                s"New state ${AnsiColor.CYAN}${PlayFsmUtils.identityOf(state)}${AnsiColor.RESET} has been expected but the transition didn't happen.",
                s""
              )
            else if (state.getClass() == thisState.getClass()) {
              val diff = Diff(thisState, state)
              MatchResult(
                false,
                s"Obtained state ${AnsiColor.CYAN}${PlayFsmUtils
                  .identityOf(state)}${AnsiColor.RESET} content differs from the expected:\n$diff}",
                s""
              )
            } else
              MatchResult(
                false,
                s"State ${AnsiColor.CYAN}${PlayFsmUtils.identityOf(state)}${AnsiColor.RESET} has been expected but got state ${AnsiColor.CYAN}${PlayFsmUtils
                  .identityOf(thisState)}${AnsiColor.RESET}",
                s""
              )

          case _ =>
            MatchResult(true, "", s"")
        }
    }

  /** Asserts that the resulting state of the transition matches some case. */
  final def thenMatch(
    statePF: PartialFunction[model.State, Unit]
  ): Matcher[When] =
    new Matcher[When] {
      override def apply(result: When): MatchResult =
        result match {
          case When(_, Left(exception)) =>
            MatchResult(
              false,
              s"Transition has been expected but got an exception: ${AnsiColor.RED}$exception${AnsiColor.RESET}",
              s""
            )

          case When(_, Right((thisState, _))) if !statePF.isDefinedAt(thisState) =>
            MatchResult(false, s"Matching state has been expected but got state $thisState", s"")

          case _ => MatchResult(true, "", s"")
        }
    }

  /** Asserts that the transition hasn't change the state. */
  final val changeNothing: Matcher[When] =
    new Matcher[When] {
      override def apply(result: When): MatchResult =
        result match {
          case When(_, Left(exception)) =>
            MatchResult(
              false,
              s"Transition has been expected but got an exception: ${AnsiColor.RED}$exception${AnsiColor.RESET}",
              s""
            )

          case When(initialState, Right((thisState, _))) if thisState != initialState =>
            MatchResult(false, s"No state change has been expected but got state $thisState", s"")

          case _ =>
            MatchResult(true, "", s"")
        }
    }

  /** Asserts that the transition threw some expected exception of type E. */
  final def failWith[E <: Throwable](implicit ct: ClassTag[E]): Matcher[When] =
    new Matcher[When] {
      private val expectedClass = ct.runtimeClass
      override def apply(result: When): MatchResult =
        result match {
          case When(_, Left(exception)) if !expectedClass.isAssignableFrom(exception.getClass) =>
            MatchResult(
              false,
              s"Exception of type ${AnsiColor.RED}${expectedClass
                .getName()}${AnsiColor.RESET} has been expected but got exception of type ${AnsiColor.RED}${exception
                .getClass()
                .getName()}${AnsiColor.RESET}",
              s""
            )

          case When(initialState, Right((thisState, _))) =>
            MatchResult(
              false,
              s"Exception of type ${AnsiColor.RED}${expectedClass.getName()}${AnsiColor.RESET} has been expected but got state $thisState",
              s""
            )

          case _ =>
            MatchResult(true, "", s"")
        }
    }

  // Delete the temp file
  override def afterAll() {
    info(s"Test suite executed ${getCounter()} state transitions in total.")
  }

}

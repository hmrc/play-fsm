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

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Journey Model is a base trait of a Finite State Machine,
  * consisting of states and transitions modelling the logic
  * of the business process flow.
  */
trait JourneyModel {

  /**
    * State can be anything but usually it will be a set of case classes or case objects
    * representing the stages and data of a business process or transaction.
    */
  type State

  /** Where your journey starts by default */
  val root: State

  /** Built-in transition from anywhere to the root state */
  final val start = Transition {
    case _ => goto(root)
  }

  /** Replace the current state with the new one. */
  final def goto(state: State): Future[State] =
    Future.successful(state)

  /** Fail the transition */
  final def fail(exception: Exception): Future[State] =
    Future.failed(exception)

  /** Stay in the current state */
  final def stay: Future[State] =
    Future.failed(StayInCurrentState)

  /**
    * Transition from one state to the another.
    *
    * Transition should be always a *pure* function, depending only on its own parameters and the current state.
    * External async requests to the upstream services should be provided as a function-type parameters.
    */
  final class Transition private (val apply: PartialFunction[State, Future[State]]) {

    /**
      * Composes this transition with the fallback transition
      * which gets applied where this transition is not defined for the curent state.
      */
    def orElse(fallback: Transition): Transition =
      Transition(apply.orElse(fallback.apply))

    /**
      * Composes this transition with the next transition
      * which gets applied to the result of this transition, if successful.
      */
    def andThen(next: Transition): Transition =
      Transition(apply.andThen(_.flatMap(state => next.apply(state))))
  }

  /** Transition builder helper */
  protected final object Transition {
    def apply(rules: PartialFunction[State, Future[State]]): Transition =
      new Transition(rules)
  }

  case class TransitionNotAllowed(
    state: State,
    breadcrumbs: List[State],
    transition: Transition
  ) extends Exception

  final case object StayInCurrentState extends Exception

  /**
    * Merger is a partial function of type `(S <: State, State) => S`,
    * used to reconcile current and previous states when rolling back the journey.
    */
  final class Merger[S <: State] private (val apply: PartialFunction[(S, State), S]) {

    /**
      * Converts merger into modification
      * by partially applying donor state parameter.
      */
    def withState(state: State): S => S = { s: S =>
      if (apply.isDefinedAt((s, state))) apply((s, state))
      else s
    }
  }

  /** Merger builder helper */
  protected final object Merger {
    def apply[S <: State](merge: PartialFunction[(S, State), S]): Merger[S] =
      new Merger(merge)
  }
}

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

import scala.concurrent.Future

/**
  * Journey Model is a base trait of a Finite State Machine,
  * consisting of states and transitions modelling the logic
  * of the business process flow.
  *
  * @see https://brilliant.org/wiki/finite-state-machines/
  */
trait JourneyModel {

  /**
    * State can be anything but usually it will be a set of case classes or objects
    * representing the progress and data of a business process or transaction.
    */
  type State

  /**
    * Transition from one state to the another.
    *
    * Transition should be always a *pure* function, depending only on its own parameters and the current state.
    * External async requests to the upstream services should be provided as a function-type parameters.
    */
  final class Transition private (val apply: PartialFunction[State, Future[State]]) {

    /**
      * Composes this transition with a fallback transition
      * which gets applied where this transition is not defined for the curent state.
      */
    def orElse(other: Transition): Transition =
      Transition(apply.orElse(other.apply))
  }

  /** Transition builder helper */
  protected object Transition {
    def apply(rules: PartialFunction[State, Future[State]]): Transition = new Transition(rules)
  }

  case class TransitionNotAllowed(state: State, breadcrumbs: List[State], transition: Transition)
      extends Exception

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
  protected object Merger {
    def apply[S <: State](merge: PartialFunction[(S, State), S]): Merger[S] = new Merger(merge)
  }

  /** Where your journey starts by default */
  val root: State

  /** Built-in transition from anywhere to the root state */
  protected[fsm] final val start = Transition {
    case _ => goto(root)
  }

  /** Replace the current state with the new one. */
  final def goto(state: State): Future[State] = Future.successful(state)

  /** Fail the transition */
  final def fail[T](exception: Exception): Future[T] = Future.failed(exception)
}

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
  * JourneyModel is an abstract base of a process diagram definition in terms of Finite State Machine pattern.
  *
  * @see https://brilliant.org/wiki/finite-state-machines/
  */
trait JourneyModel {

  type State

  final class Transition private (val apply: PartialFunction[State, Future[State]])

  /** Transition builder helper */
  protected object Transition {
    def apply(rules: PartialFunction[State, Future[State]]): Transition = new Transition(rules)
  }

  case class TransitionNotAllowed(state: State, breadcrumbs: List[State], transition: Transition)
      extends Exception

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

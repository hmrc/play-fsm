/*
 * Copyright 2019 HM Revenue & Customs
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

import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.{ExecutionContext, Future}

/**
  * JourneyService is an abstract base of components exposing journey to the application (controller)
  */
trait JourneyService {

  val journeyKey: String
  val model: JourneyModel

  type Breadcrumbs         = List[model.State]
  type StateAndBreadcrumbs = (model.State, Breadcrumbs)

  /** Applies transition to the current state and returns new state or error */
  def apply(transition: model.Transition)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[StateAndBreadcrumbs]

  /** Returns current state (if any) and breadcrumbs */
  def currentState(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[StateAndBreadcrumbs]]

  /** Returns initial state found in breadcrumbs or the root state if breadcrumbs are empty */
  def initialState(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[model.State]

  /** Steps back to previous state and breadcrumbs (if any) */
  def stepBack(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[StateAndBreadcrumbs]]

  /** Cleans breadcrumbs from the session and returns removed list */
  def cleanBreadcrumbs(
    filter: Breadcrumbs => Breadcrumbs)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Breadcrumbs]
}

/**
  * This trait enhances JourneyService with StateAndBreadcrumbs persistence abstractions.
  */
trait PersistentJourneyService extends JourneyService {

  protected def fetch(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[StateAndBreadcrumbs]]
  protected def save(
    state: StateAndBreadcrumbs)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[StateAndBreadcrumbs]

  /**
    * Should clear state and breadcrumbs. Default implementation does nothing.
    * Override to make the desired effect in your persistence layer of choice.
    * Put here to enable an interaction between controller and persistence layers.
    **/
  def clear(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = Future.successful(())

  /**
    * Default retention strategy is to keep all visited states.
    * Override to provide your own strategy.
    */
  val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs = identity

  override def apply(
    transition: model.Transition)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[StateAndBreadcrumbs] =
    for {
      initialStateAndBreadcrumbsOpt <- fetch
      endStateOrError <- {
        val (state, breadcrumbs) = initialStateAndBreadcrumbsOpt match {
          case Some(sab) => sab
          case None      => (model.root, Nil)
        }
        if (transition.apply.isDefinedAt(state)) transition.apply(state) flatMap { endState =>
          save((endState, if (endState == state) breadcrumbs else state :: breadcrumbsRetentionStrategy(breadcrumbs)))
        } else
          // throw an exception to give outer layer a chance to stay in sync (e.g. redirect back to the current state)
          model.fail(model.TransitionNotAllowed(state, breadcrumbs, transition))
      }
    } yield endStateOrError

  override def currentState(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[StateAndBreadcrumbs]] =
    fetch

  override def initialState(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[model.State] =
    for {
      stateAndBreadcrumbsOpt <- fetch
      initialState <- Future.successful {
                       stateAndBreadcrumbsOpt match {
                         case None                   => model.root
                         case Some((_, breadcrumbs)) => breadcrumbs.last
                       }
                     }
    } yield initialState

  override def stepBack(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[StateAndBreadcrumbs]] =
    fetch
      .flatMap {
        case Some((_, breadcrumbs)) =>
          breadcrumbs match {
            case Nil    => Future.successful(None)
            case s :: b => save((s, b)).map(Option.apply)
          }
        case None => Future.successful(None)
      }

  override def cleanBreadcrumbs(filter: Breadcrumbs => Breadcrumbs = _ => Nil)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Breadcrumbs] =
    for {
      stateAndBreadcrumbsOpt <- fetch
      breadcrumbs <- stateAndBreadcrumbsOpt match {
                      case None                       => Future.successful(Nil)
                      case Some((state, breadcrumbs)) => save((state, filter(breadcrumbs))).map(_ => breadcrumbs)
                    }
    } yield breadcrumbs

}

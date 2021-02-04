/*
 * Copyright 2021 HM Revenue & Customs
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

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  * JourneyService is an abstract base of components exposing journey state
  * to the application controller.
  *
  * @tparam RequestContext type of the context object available implicitly across all actions
  */
trait JourneyService[RequestContext] {

  val journeyKey: String
  val model: JourneyModel

  type Breadcrumbs         = List[model.State]
  type StateAndBreadcrumbs = (model.State, Breadcrumbs)

  /** Applies transition to the current state and returns new state or error */
  def apply(
    transition: model.Transition
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs]

  /** Applies modification to the current state and returns new state */
  protected[fsm] def modify[S <: model.State: ClassTag](
    modify: S => S
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs]

  /** Returns current state (if any) and breadcrumbs */
  def currentState(implicit
    rc: RequestContext,
    ec: ExecutionContext
  ): Future[Option[StateAndBreadcrumbs]]

  /** Returns initial state found in breadcrumbs or the root state if breadcrumbs are empty */
  def initialState(implicit rc: RequestContext, ec: ExecutionContext): Future[model.State]

  /** Steps back to previous state and breadcrumbs (if any) */
  def stepBack(implicit
    rc: RequestContext,
    ec: ExecutionContext
  ): Future[Option[StateAndBreadcrumbs]]

  /** Cleans breadcrumbs from the session and returns removed list */
  def cleanBreadcrumbs(
    filter: Breadcrumbs => Breadcrumbs
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[Breadcrumbs]
}

/**
  * This trait enhances JourneyService with StateAndBreadcrumbs persistence abstractions.
  */
trait PersistentJourneyService[RequestContext] extends JourneyService[RequestContext] {

  protected def fetch(implicit
    rc: RequestContext,
    ec: ExecutionContext
  ): Future[Option[StateAndBreadcrumbs]]

  protected def save(
    state: StateAndBreadcrumbs
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs]

  /**
    * Should clear state and breadcrumbs. Default implementation does nothing.
    * Override to make the desired effect in your persistence layer of choice.
    * Put here to enable an interaction between controller and persistence layers.
    */
  def clear(implicit rc: RequestContext, ec: ExecutionContext): Future[Unit] = Future.successful(())

  /**
    * Default retention strategy is to keep all visited states.
    * Override to provide your own strategy.
    */
  val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs = identity

  override def apply(
    transition: model.Transition
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs] =
    for {
      initialStateAndBreadcrumbsOpt <- fetch
      endStateOrError <- {
        val (state, breadcrumbs) = initialStateAndBreadcrumbsOpt match {
          case Some(sab) => sab
          case None      => (model.root, Nil)
        }
        if (transition.apply.isDefinedAt(state)) transition.apply(state) flatMap { endState =>
          save(
            (
              endState,
              if (endState == state) breadcrumbs
              else state :: breadcrumbsRetentionStrategy(breadcrumbs)
            )
          )
        }
        else
          // throw an exception to give outer layer a chance to stay in sync (e.g. redirect back to the current state)
          model.fail(model.TransitionNotAllowed(state, breadcrumbs, transition))
      }
    } yield endStateOrError

  override protected[fsm] def modify[S <: model.State: ClassTag](
    modification: S => S
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs] =
    for {
      initialStateAndBreadcrumbsOpt <- fetch
      stateAndBreadcrumbs <- initialStateAndBreadcrumbsOpt match {
                               case Some((state, breadcrumbs))
                                   if implicitly[ClassTag[S]].runtimeClass.isAssignableFrom(
                                     state.getClass
                                   ) =>
                                 val newState = modification(state.asInstanceOf[S])
                                 if (newState != state) save((newState, breadcrumbs))
                                 else Future.successful((newState, breadcrumbs))
                               case _ => Future.successful((model.root, Nil))
                             }
    } yield stateAndBreadcrumbs

  override def currentState(implicit
    rc: RequestContext,
    ec: ExecutionContext
  ): Future[Option[StateAndBreadcrumbs]] =
    fetch

  override def initialState(implicit
    rc: RequestContext,
    ec: ExecutionContext
  ): Future[model.State] =
    for {
      stateAndBreadcrumbsOpt <- fetch
      initialState <- Future.successful {
                        stateAndBreadcrumbsOpt match {
                          case None => model.root
                          case Some((_, breadcrumbs)) =>
                            breadcrumbs.lastOption.getOrElse(model.root)
                        }
                      }
    } yield initialState

  override def stepBack(implicit
    rc: RequestContext,
    ec: ExecutionContext
  ): Future[Option[StateAndBreadcrumbs]] =
    fetch
      .flatMap {
        case Some((_, breadcrumbs)) =>
          breadcrumbs match {
            case Nil    => Future.successful(None)
            case s :: b => save((s, b)).map(Option.apply)
          }
        case None => Future.successful(None)
      }

  override def cleanBreadcrumbs(
    filter: Breadcrumbs => Breadcrumbs = _ => Nil
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[Breadcrumbs] =
    for {
      stateAndBreadcrumbsOpt <- fetch
      breadcrumbs <- stateAndBreadcrumbsOpt match {
                       case None => Future.successful(Nil)
                       case Some((state, breadcrumbs)) =>
                         save((state, filter(breadcrumbs))).map(_ => breadcrumbs)
                     }
    } yield breadcrumbs

}

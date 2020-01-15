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

import org.scalatest.matchers.{MatchResult, Matcher}

trait StateAndBreadcrumbsMatchers {

  def have[S](state: S, breadcrumbs: List[S]): Matcher[Option[(S, List[S])]] =
    new Matcher[Option[(S, List[S])]] {
      override def apply(result: Option[(S, List[S])]): MatchResult = result match {
        case Some((thisState, thisBreadcrumbs))
            if state == thisState && breadcrumbs == thisBreadcrumbs =>
          MatchResult(true, "", s"End state $state as expected")
        case Some((thisState, thisBreadcrumbs))
            if state == thisState && breadcrumbs != thisBreadcrumbs =>
          MatchResult(
            false,
            s"End state $state as expected but breadcrumbs different $breadcrumbs != $thisBreadcrumbs",
            s"")
        case Some((thisState, _)) if state != thisState =>
          MatchResult(false, s"End state $state has been expected but got state $thisState", s"")
        case None =>
          MatchResult(false, s"Some state $state has been expected but got None", s"")
      }
    }

  def have[S](state: S): Matcher[Option[(S, List[S])]] =
    new Matcher[Option[(S, List[S])]] {
      override def apply(result: Option[(S, List[S])]): MatchResult = result match {
        case Some((thisState, _)) if state == thisState =>
          MatchResult(true, "", s"End state $state as expected")
        case Some((thisState, _)) if state != thisState =>
          MatchResult(false, s"End state $state has been expected but got state $thisState", s"")
        case None =>
          MatchResult(false, s"Some state $state has been expected but got None", s"")
      }
    }

  def havePattern[S](
    statePF: PartialFunction[S, Unit],
    breadcrumbs: List[S]): Matcher[Option[(S, List[S])]] =
    new Matcher[Option[(S, List[S])]] {
      override def apply(result: Option[(S, List[S])]): MatchResult = result match {
        case Some((thisState, thisBreadcrumbs))
            if statePF.isDefinedAt(thisState) && breadcrumbs == thisBreadcrumbs =>
          MatchResult(true, "", s"End state as expected")
        case Some((thisState, thisBreadcrumbs))
            if statePF.isDefinedAt(thisState) && breadcrumbs != thisBreadcrumbs =>
          MatchResult(false, s"End state as expected but breadcrumbs different", s"")
        case Some((thisState, _)) if !statePF.isDefinedAt(thisState) =>
          MatchResult(false, s"End state has been expected but got state $thisState", s"")
        case None =>
          MatchResult(false, s"Some state has been expected but got None", s"")
      }
    }

}

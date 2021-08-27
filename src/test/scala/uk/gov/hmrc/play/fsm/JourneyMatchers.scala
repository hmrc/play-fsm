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

import org.scalatest.matchers.{MatchResult, Matcher}

trait JourneyMatchers {

  val model: JourneyModel

  def thenGo(state: model.State): Matcher[(model.State, List[model.State])] =
    new Matcher[(model.State, List[model.State])] {
      override def apply(result: (model.State, List[model.State])): MatchResult =
        result match {
          case (thisState, _) if state != thisState =>
            MatchResult(false, s"State $state has been expected but got state $thisState", s"")
          case (thisState, _) if state == thisState =>
            MatchResult(true, "", s"")
        }
    }

  def thenMatch(
    statePF: PartialFunction[model.State, Unit]
  ): Matcher[(model.State, List[model.State])] =
    new Matcher[(model.State, List[model.State])] {
      override def apply(result: (model.State, List[model.State])): MatchResult =
        result match {
          case (thisState, _) if !statePF.isDefinedAt(thisState) =>
            MatchResult(false, s"Matching state has been expected but got state $thisState", s"")
          case _ => MatchResult(true, "", s"")
        }
    }

}

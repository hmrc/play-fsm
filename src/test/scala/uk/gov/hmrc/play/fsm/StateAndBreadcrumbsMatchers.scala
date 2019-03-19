package uk.gov.hmrc.play.fsm

import org.scalatest.matchers.{MatchResult, Matcher}

trait StateAndBreadcrumbsMatchers {

  def have[S](state: S, breadcrumbs: List[S]): Matcher[Option[(S, List[S])]] =
    new Matcher[Option[(S, List[S])]] {
      override def apply(result: Option[(S, List[S])]): MatchResult = result match {
        case Some((thisState, thisBreadcrumbs)) if state == thisState && breadcrumbs == thisBreadcrumbs =>
          MatchResult(true, "", s"End state $state as expected")
        case Some((thisState, thisBreadcrumbs)) if state == thisState && breadcrumbs != thisBreadcrumbs =>
          MatchResult(false, s"End state $state as expected but breadcrumbs different", s"")
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

  def havePattern[S](statePF: PartialFunction[S, Unit], breadcrumbs: List[S]): Matcher[Option[(S, List[S])]] =
    new Matcher[Option[(S, List[S])]] {
      override def apply(result: Option[(S, List[S])]): MatchResult = result match {
        case Some((thisState, thisBreadcrumbs)) if statePF.isDefinedAt(thisState) && breadcrumbs == thisBreadcrumbs =>
          MatchResult(true, "", s"End state as expected")
        case Some((thisState, thisBreadcrumbs)) if statePF.isDefinedAt(thisState) && breadcrumbs != thisBreadcrumbs =>
          MatchResult(false, s"End state as expected but breadcrumbs different", s"")
        case Some((thisState, _)) if !statePF.isDefinedAt(thisState) =>
          MatchResult(false, s"End state has been expected but got state $thisState", s"")
        case None =>
          MatchResult(false, s"Some state has been expected but got None", s"")
      }
    }

}

package uk.gov.hmrc.play.fsm

class TestJourneyModel extends JourneyModel {

  type State = String

  /** Where your journey starts by default */
  override val root: State = "start"

  object Transitions {

    def append(suffix: String) = Transition {
      case s => goto(s + suffix)
    }

    def reverse = Transition {
      case s => goto(s.reverse)
    }

    def replace(a: String, b: String) = Transition {
      case s => goto(s.replaceAllLiterally(a, b))
    }

  }
}

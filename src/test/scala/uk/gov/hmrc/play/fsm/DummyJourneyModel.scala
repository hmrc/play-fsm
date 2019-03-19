package uk.gov.hmrc.play.fsm

object DummyJourneyModel extends JourneyModel {

  sealed trait State

  object State {
    case object Start extends State
    case class Continue(arg: String) extends State
    case class Stop(result: String) extends State
  }

  override val root: State = State.Start

  object Transitions {
    import State._

    def continue(user: Int)(arg: String) = Transition {
      case Start          => goto(Continue(arg))
      case Continue(curr) => goto(Continue(curr + "," + arg))
    }

    def stop(user: Int) = Transition {
      case Start          => goto(Stop(""))
      case Continue(curr) => goto(Stop(curr))
    }
  }

}

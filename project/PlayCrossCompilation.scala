import uk.gov.hmrc.playcrosscompilation.AbstractPlayCrossCompilation
import uk.gov.hmrc.playcrosscompilation.PlayVersion.{Play26, Play27, Play28}

object PlayCrossCompilation extends AbstractPlayCrossCompilation(defaultPlayVersion = Play28) {

  override lazy val playDir = playVersion match {
    case Play26 => "play-26"
    case Play27 => "play-28" // has changed to "play-28" after 2.7.7
    case Play28 => "play-28"
  }
}

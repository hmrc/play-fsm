import PlayCrossCompilation._
import play.core.PlayVersion

val libName = "play-fsm"

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regex matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimum := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val library = Project(libName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
  .disablePlugins(PlayLayoutPlugin)
  .settings(
    makePublicallyAvailableOnBintray := true,
    majorVersion := 0
  )
  .settings(
    name := libName,
    scalaVersion := "2.12.12",
    libraryDependencies ++= PlayCrossCompilation.dependencies(
      shared = Seq(
        "org.scalatest"     %% "scalatest"  % "3.0.8"             % Test,
        "org.pegdown"        % "pegdown"    % "1.6.0"             % Test,
        "org.scalacheck"    %% "scalacheck" % "1.14.3"            % Test,
        "com.typesafe.play" %% "play-test"  % PlayVersion.current % Test
      ),
      play25 = Seq(
        "com.typesafe.play"      %% "play-json"          % "2.5.19",
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % Test
      ),
      play26 = Seq(
        "com.typesafe.play"      %% "play-json"          % "2.6.14",
        "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % Test
      ),
      play27 = Seq(
        "com.typesafe.play"      %% "play-json"          % "2.7.4",
        "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test
      )
    ),
    crossScalaVersions := List("2.11.12", "2.12.12"),
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      "typesafe-releases" at "https://repo.typesafe.com/typesafe/releases/"
    ),
    dependencyOverrides += "com.typesafe.play" %% "twirl-api" % "1.4.2",
    playCrossCompilationSettings,
    scalafmtOnCompile in Compile := true,
    scalafmtOnCompile in Test := true,
    scoverageSettings
  )

resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("uk.gov.hmrc"    % "sbt-distributables"    % "2.2.0")
addSbtPlugin("uk.gov.hmrc"    % "sbt-auto-build"        % "3.9.0")
addSbtPlugin("org.scoverage"  % "sbt-scoverage"         % "2.0.7")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("uk.gov.hmrc"    % "sbt-play-cross-compilation" % "2.3.0")
// added to get around the scoverage compile errors for scala 2.13.10
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
addDependencyTreePlugin

val playPlugin =
  if (sys.env.get("PLAY_VERSION").contains("2.6"))
    "com.typesafe.play" % "sbt-plugin" % "2.6.25"
  else if (sys.env.get("PLAY_VERSION").contains("2.7"))
    "com.typesafe.play" % "sbt-plugin" % "2.7.9"
  else
    "com.typesafe.play" % "sbt-plugin" % "2.8.18"

addSbtPlugin(playPlugin)


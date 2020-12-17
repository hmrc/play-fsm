resolvers += Resolver.url(
  "HMRC Sbt Plugin Releases",
  url("https://dl.bintray.com/hmrc/sbt-plugin-releases")
)(Resolver.ivyStylePatterns)
resolvers += "HMRC Releases" at "https://dl.bintray.com/hmrc/releases"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "2.12.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "2.1.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "2.2.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "1.11.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")

addSbtPlugin("uk.gov.hmrc" % "sbt-play-cross-compilation" % "1.2.0")

val playPlugin =
  if (sys.env.get("PLAY_VERSION").contains("2.7"))
    "com.typesafe.play" % "sbt-plugin" % "2.7.8"
  else
    "com.typesafe.play" % "sbt-plugin" % "2.6.25"

addSbtPlugin(playPlugin)

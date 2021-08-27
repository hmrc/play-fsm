resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url(
  "HMRC-open-artefacts-ivy",
  url("https://open.artefacts.tax.service.gov.uk/ivy2")
)(
  Resolver.ivyStylePatterns
)

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "3.5.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "2.1.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")

addSbtPlugin("uk.gov.hmrc" % "sbt-play-cross-compilation" % "2.2.0")

val playPlugin =
  if (sys.env.get("PLAY_VERSION").contains("2.6"))
    "com.typesafe.play" % "sbt-plugin" % "2.6.25"
  else if (sys.env.get("PLAY_VERSION").contains("2.7"))
    "com.typesafe.play" % "sbt-plugin" % "2.7.9"
  else
    "com.typesafe.play" % "sbt-plugin" % "2.8.8"

addSbtPlugin(playPlugin)

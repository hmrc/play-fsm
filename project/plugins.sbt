resolvers += Resolver.url("HMRC Sbt Plugin Releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(
  Resolver.ivyStylePatterns)
resolvers += "HMRC Releases" at "https://dl.bintray.com/hmrc/releases"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.16.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "1.20.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "0.21.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")

addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")

addSbtPlugin("uk.gov.hmrc" % "sbt-play-cross-compilation" % "0.18.0")

val playPlugin =
  if (sys.env.get("PLAY_VERSION").exists(_ == "2.7"))
    "com.typesafe.play" % "sbt-plugin" % "2.7.3"
  else if (sys.env.get("PLAY_VERSION").exists(_ == "2.6"))
    "com.typesafe.play" % "sbt-plugin" % "2.6.24"
  else
    "com.typesafe.play" % "sbt-plugin" % "2.5.19"

addSbtPlugin(playPlugin)

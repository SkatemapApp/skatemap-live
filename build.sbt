import BuildCommands.{ciBuild, devBuild}

name := """skatemap-live"""
organization := "skatemap.org"

version := "1.0-SNAPSHOT"

coverageMinimumStmtTotal := 100
coverageFailOnMinimum := true

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .settings(
    commands ++= Seq(ciBuild, devBuild),
    coverageExcludedPackages := "<empty>;Reverse.*;router\\.*",
    // WartRemover incorrectly analyzes routes files - exclude warts that routes files trigger
    Compile / compile / wartremoverErrors := Warts.allBut(
      // Core exclusions for legitimate use cases
      Wart.Any, Wart.NonUnitStatements, Wart.Nothing, Wart.Serializable,
      // Play Framework routes file exclusions (should be fixed by proper exclusion)
      Wart.AsInstanceOf, Wart.JavaSerializable, Wart.Overloading, Wart.Var, Wart.Product
    ),
    Test / compile / wartremoverErrors := Warts.allBut(Wart.Any, Wart.NonUnitStatements, Wart.Nothing, Wart.Serializable)
  )

scalaVersion := "2.13.16"

scalacOptions ++= Seq(
  "-Wunused:locals",
  "-Wunused:privates",
  "-Wunused:imports",
  "-Wunused:patvars",
  "-Xfatal-warnings",
  "-Wconf:src=routes/.*:s"
)

// Attempt to exclude routes file from WartRemover (doesn't work properly)
wartremoverExcluded += (Compile / resourceDirectory).value

libraryDependencies ++= Seq(
  guice,
  "org.apache.pekko" %% "pekko-stream" % "1.1.2",
  "org.apache.pekko" %% "pekko-slf4j" % "1.1.2",
  "org.apache.pekko" %% "pekko-serialization-jackson" % "1.1.2",
  "org.apache.pekko" %% "pekko-actor-typed" % "1.1.2",
  "org.apache.pekko" %% "pekko-testkit" % "1.1.2" % Test,
  "org.apache.pekko" %% "pekko-stream-testkit" % "1.1.2" % Test,
  "org.apache.pekko" %% "pekko-http-testkit" % "1.2.0" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
  "org.scalacheck" %% "scalacheck" % "1.18.1" % Test,
  "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test
)

libraryDependencies := libraryDependencies.value.map {
  case m if m.organization == "org.playframework" =>
    m.excludeAll(
      ExclusionRule("org.playframework", "twirl-api_2.13"),
      ExclusionRule("org.playframework", "play-ws_2.13"),
      ExclusionRule("org.playframework", "play-filters-helpers_2.13")
    )
  case m => m
}

routesGenerator := InjectedRoutesGenerator

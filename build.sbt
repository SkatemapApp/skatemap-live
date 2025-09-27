import CiCommands.ciBuild

name := """skatemap-live"""
organization := "skatemap.org"

version := "1.0-SNAPSHOT"

coverageMinimumStmtTotal := 100
coverageFailOnMinimum := true

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .settings(commands += ciBuild,
    coverageExcludedPackages := "<empty>;Reverse.*;router\\.*",
      Compile / compile / wartremoverErrors := Seq(Wart.DefaultArguments),
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

libraryDependencies ++= Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
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

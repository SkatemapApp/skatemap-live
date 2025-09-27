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
      Test / compile / wartremoverErrors := Warts.allBut(Wart.Any, Wart.NonUnitStatements, Wart.Nothing, Wart.Serializable)
  )

scalaVersion := "2.13.14"

libraryDependencies ++= Seq(
  guice,                                                     // dependency injection
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
)

// Exclude unused Play dependencies for API-only service
libraryDependencies := libraryDependencies.value.map {
  case m if m.organization == "org.playframework" =>
    m.excludeAll(
      ExclusionRule("org.playframework", "twirl-api_2.13"),
      ExclusionRule("org.playframework", "play-ws_2.13"),
      ExclusionRule("org.playframework", "play-filters-helpers_2.13")
    )
  case m => m
}

// Disable Twirl template engine for API-only service
routesGenerator := InjectedRoutesGenerator

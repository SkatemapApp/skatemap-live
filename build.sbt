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
    coverageExcludedPackages := "views\\.html;<empty>;Reverse.*;router\\.*",
      Test / compile / wartremoverErrors := Warts.allBut(Wart.Any, Wart.NonUnitStatements, Wart.Nothing, Wart.Serializable)
  )

scalaVersion := "2.13.14"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "skatemap.org.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "skatemap.org.binders._"

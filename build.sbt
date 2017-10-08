name := """skatemap-live"""
organization := "com.swifthorseman"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.3"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.swifthorseman.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.swifthorseman.binders._"

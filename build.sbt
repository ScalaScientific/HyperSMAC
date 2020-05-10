organization := "com.scalascis"

name := "HyperSMAC"

version := "0.1.0-SNAPSHOT"

lazy val scala212 = "2.12.10"
lazy val scala211 = "2.11.12"
lazy val supportedScalaVersions = List(scala212, scala211)

ThisBuild / organization := "com.scalasci"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala212

lazy val root = (project in file("."))
  .settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    crossScalaVersions := Nil,
    publish / skip := true
  )

libraryDependencies += "com.github.haifengl" %% "smile-scala" % "2.4.0"
libraryDependencies += "com.github.haifengl" % "smile-core" % "2.4.0"
libraryDependencies += "com.github.haifengl" % "smile-plot" % "2.4.0"
libraryDependencies += "com.github.haifengl" % "smile-data" % "2.4.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % "test"
libraryDependencies += "org.deeplearning4j" % "deeplearning4j-core" % "1.0.0-beta6" % "test"
libraryDependencies += "org.nd4j" % "nd4j-native-platform" % "1.0.0-beta6" % "test"

resolvers += Resolver.bintrayRepo("cibotech", "public")

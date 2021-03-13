import sbt.Plugins.allRequirements

organization := "com.scalasci"

name := "HyperSMAC"

version := "0.1.0-SNAPSHOT"

lazy val scala213 = "2.13.2"
lazy val scala212 = "2.12.10"
lazy val scala211 = "2.11.12"
lazy val supportedScalaVersions = List(scala213, scala212, scala211)

githubOwner := "scalasci"
githubRepository := "HyperSMAC"

ThisBuild / organization := "com.scalasci"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala212

val isCiBuild = sys.env.exists{case (key, _) => key=="GITHUB_TOKEN"}

val disableCiPlugins = if(!isCiBuild){
  List(GitHubPackagesPlugin)
}else{
  List.empty
}

lazy val root = (project in file("."))
  .settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    crossScalaVersions := Nil,
    publish / skip := true
  ).disablePlugins(disableCiPlugins:_*)

libraryDependencies += "com.github.haifengl" %% "smile-scala" % "2.4.0"
libraryDependencies += "com.github.haifengl" % "smile-core" % "2.4.0"
libraryDependencies += "com.github.haifengl" % "smile-plot" % "2.4.0"
libraryDependencies += "com.github.haifengl" % "smile-data" % "2.4.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % "test"
libraryDependencies += "org.deeplearning4j" % "deeplearning4j-core" % "1.0.0-beta6" % "test"
libraryDependencies += "org.nd4j" % "nd4j-native-platform" % "1.0.0-beta6" % "test"

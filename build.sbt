import scala.sys.process.Process

organization := "com.scalasci"

name := "HyperSMAC"

lazy val scala213 = "2.13.2"
lazy val scala212 = "2.12.10"
//lazy val scala211 = "2.11.12"  // not supported. please upgrade.
lazy val supportedScalaVersions = List(scala213, scala212)

githubOwner := "ScalaScientific"
githubRepository := "HyperSMAC"

organization := "com.scalasci"
version := "0.1.1"

scalaVersion := scala212

val gitBranch = settingKey[String]("Determines current git branch")

gitBranch := {
  val branch = Process("git rev-parse --abbrev-ref HEAD").lineStream.head
  val log = sLog.value
  log.info(s"git branch = ${branch}")
  branch
}

//trying to be friendly to third-party devs' local builds
val isCiBuild = sys.env.exists{case (key, _) => key=="GITHUB_TOKEN"}
val disableCiPlugins = if(!isCiBuild){
  List(GitHubPackagesPlugin)
}else{
  List.empty
}

lazy val root = (project in file("."))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    publish / skip := gitBranch.value != "HEAD",
  ).disablePlugins(disableCiPlugins:_*)

libraryDependencies += "com.github.haifengl" %% "smile-scala" % "2.4.0"
libraryDependencies += "com.github.haifengl" % "smile-core" % "2.4.0"
libraryDependencies += "com.github.haifengl" % "smile-plot" % "2.4.0"
libraryDependencies += "com.github.haifengl" % "smile-data" % "2.4.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % "test"
libraryDependencies += "org.deeplearning4j" % "deeplearning4j-core" % "1.0.0-beta6" % "test"
libraryDependencies += "org.nd4j" % "nd4j-native-platform" % "1.0.0-beta6" % "test"

import scala.sys.process.Process

organization := "com.scalasci"

name := "HyperSMAC"

lazy val smileVersion = "2.4.0"

lazy val scala213 = "2.13.2"
lazy val scala212 = "2.12.10"
//lazy val scala211 = "2.11.12"  // not supported. please upgrade.
lazy val supportedScalaVersions = List(scala213, scala212)
scalaVersion := scala212

organization := "com.scalasci"
version := "0.1.6-SNAPSHOT"

ThisBuild / organizationHomepage := Some(url("https://github.com/ScalaScientific"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/ScalaScientific"),
    "scm:git@github.com:ScalaScientific/HyperSMAC.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "scalasci",
    name  = "Scala Scientific",
    email = "scalasci@users.noreply.github.com",
    url   = url("https://github.com/ScalaScientific")
  )
)

ThisBuild / description := "A hyperband + smac hyperparameter optimization utility."
ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/ScalaScientific"))

val gitBranch = settingKey[String]("Determines current git branch")

gitBranch := {
  val branch = Process("git rev-parse --abbrev-ref HEAD").lineStream.head
  val log = sLog.value
  log.info(s"git branch = ${branch}")
  branch
}

lazy val root = (project in file("."))
  .settings(
      crossScalaVersions := supportedScalaVersions,
      publish / skip := false,
      publishTo := sonatypePublishToBundle.value,
      sonatypeCredentialHost := "s01.oss.sonatype.org"
    )

// even though we eventually aim to become zero-dependency, we presently bring in smile for XG boost and plotting.
libraryDependencies += "com.github.haifengl" %% "smile-scala" % smileVersion
libraryDependencies += "com.github.haifengl" % "smile-core" % smileVersion
libraryDependencies += "com.github.haifengl" % "smile-plot" % smileVersion
libraryDependencies += "com.github.haifengl" % "smile-data" % smileVersion
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % "test"
libraryDependencies += "org.deeplearning4j" % "deeplearning4j-core" % "1.0.0-beta6" % "test"
libraryDependencies += "org.nd4j" % "nd4j-native-platform" % "1.0.0-beta6" % "test"

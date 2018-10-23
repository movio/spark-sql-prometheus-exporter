name := "spark-sql-prometheus-exporter"
organization := "co.movio"
version := "0.0.4"

scalaVersion := "2.11.11"

scalacOptions := Seq(
  "-Xlint",
  "-deprecation",
  "-feature",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ywarn-unused"
)

libraryDependencies ++= Seq(
  "io.prometheus" % "simpleclient_pushgateway" % "0.2.0",
  "org.apache.spark" %% "spark-sql" % "2.2.1" % Provided,
  "com.monovore" %% "decline" % "0.4.0",
  "org.tpolecat" %% "atto-core" % "0.6.2-M1"
)

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

// Override the directory structure settings so that subprojects have the
// following flattened layout:
//
// build.sbt
// resources/
//   application.conf
// src/
//   A.scala
// test/
//   ATests.scala

sourceDirectory in Compile := baseDirectory.value / "src"
sourceDirectory in Test := baseDirectory.value / "test"

scalaSource in Compile := baseDirectory.value / "src"
scalaSource in Test := baseDirectory.value / "test"

resourceDirectory in Compile := baseDirectory.value / "resources"
resourceDirectory in Test := baseDirectory.value / "resources_test"

// Configure Scaladoc and GitHub pages publishing.
// Run `scaladoc` in SBT to push.

scalafmtOnCompile in ThisProject := true

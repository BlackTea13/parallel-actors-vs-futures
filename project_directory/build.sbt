ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.1.3"

lazy val akkaVersion = "2.6.19"


lazy val root = (project in file("."))
  .settings(
    name := "parallel_duel_project"
  )


libraryDependencies ++= Seq(
  "org.jsoup" % "jsoup" % "1.14.3",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.2.12"
)

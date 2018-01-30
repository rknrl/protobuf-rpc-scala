organization := "ru.rknrl"

name := "rpc"

version := "1.1.1"

scalaVersion := "2.11.12"

val akkaVersion = "2.5.9"
val akkaHttpVersion = "10.0.11"
val scalaPbVersion = "0.4.19"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.trueaccord.scalapb" %% "scalapb-runtime" % scalaPbVersion
)

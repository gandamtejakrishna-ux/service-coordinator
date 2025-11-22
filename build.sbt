ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version := "1.0.0"

lazy val akkaVersion = "2.8.8"
// add to your existing build.sbt dependencies Seq(...)
libraryDependencies += "org.eclipse.angus" % "angus-mail" % "2.0.2"
libraryDependencies += "com.sun.mail" % "jakarta.mail" % "2.0.1"
libraryDependencies += "com.sun.activation" % "jakarta.activation" % "2.0.1"

lazy val root = (project in file("."))
  .settings(
    name := "service-coordinator",
    organization := "com.hotel",

    libraryDependencies ++= Seq(
      // Akka Typed Actors
      //      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      //      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      //"com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,

      // Akka Streams
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,

      // Akka Stream Kafka (Alpakka Kafka)
      "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.2",

      // JSON (Play JSON to keep compatibility with Booking + Reception)
      "com.typesafe.play" %% "play-json" % "2.9.4",

      // Kafka Client (low-level producer/consumer)
      "org.apache.kafka" % "kafka-clients" % "3.7.0",

      // Logging
      "ch.qos.logback" % "logback-classic" % "1.4.11"
    ),

    resolvers += "Akka library repository".at("https://repo.akka.io/maven"),
    fork := true


  )

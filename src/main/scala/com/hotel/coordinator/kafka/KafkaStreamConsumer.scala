//package com.hotel.coordinator.kafka
//
//import akka.actor.typed.ActorSystem
//import akka.stream.scaladsl.{RestartSource, Sink}
//import akka.stream.{RestartSettings, SystemMaterializer}
//import akka.kafka.ConsumerMessage.CommittableMessage
//import akka.kafka.scaladsl.Consumer
//import akka.kafka.{ConsumerSettings, Subscriptions}
//import org.apache.kafka.common.serialization.StringDeserializer
//import play.api.libs.json._
//import scala.concurrent.duration._
//import org.slf4j.LoggerFactory
//import com.hotel.coordinator.actors.NotificationSupervisor
//
//object KafkaStreamConsumer {
//  private val logger = LoggerFactory.getLogger(this.getClass)
//
//  def start(supervisorSystem: ActorSystem[NotificationSupervisor.Command]): Unit = {
//    implicit val system = supervisorSystem
//    implicit val ec = system.executionContext
//    val config = system.settings.config
//
//    val bootstrapServers = config.getString("kafka.bootstrap-servers")
//    val topic = config.getString("kafka.topic")
//    val groupId = config.getString("kafka.group-id")
//
//    val consumerSettings =
//      ConsumerSettings(system.classicSystem, new StringDeserializer, new StringDeserializer)
//        .withBootstrapServers(bootstrapServers)
//        .withGroupId(groupId)
//        .withProperty("auto.offset.reset", "earliest")
//
//    val mat = SystemMaterializer(system).materializer
//
//    logger.info(s"Starting Kafka consumer for topic=$topic group=$groupId")
//
//    RestartSource.onFailuresWithBackoff(
//        RestartSettings(3.seconds, 30.seconds, 0.2)
//      ) { () =>
//        Consumer
//          .committableSource(consumerSettings, Subscriptions.topics(topic)) // <-- SOURCE ✔
//          .mapAsync(1) { msg =>
//            val jsonTry: scala.util.Try[JsValue] =
//              scala.util.Try(Json.parse(msg.record.value()))
//
//
//            val js: JsValue = jsonTry match {
//              case scala.util.Success(value) =>
//                value
//              case scala.util.Failure(ex) =>
//                logger.error(s"Invalid JSON: ${msg.record.value()} ex=${ex.getMessage}")
//                Json.obj("error" -> "invalid-json")
//            }
//
//            // Send JSON to Actor Supervisor
//            supervisorSystem ! NotificationSupervisor.IncomingEvent(js)
//
//            // Commit Kafka offset but return Future[Unit]
//            msg.committableOffset.commitScaladsl().map(_ => ())
//          }
//
//      }
//      .runWith(Sink.ignore)(mat)   // <-- run the whole RestartSource using runWith
//
//  }
//}

package com.hotel.coordinator.kafka

import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{RestartSource, Sink}
import akka.stream.{RestartSettings, SystemMaterializer}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.libs.json._
import scala.concurrent.duration._
import org.slf4j.LoggerFactory
import com.hotel.coordinator.actors.NotificationSupervisor

object KafkaStreamConsumer {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def start(supervisorSystem: ActorSystem[NotificationSupervisor.Command]): Unit = {
    implicit val system = supervisorSystem
    implicit val ec = system.executionContext
    val config = system.settings.config

    val bootstrapServers = config.getString("kafka.bootstrap-servers")
    val topic = config.getString("kafka.topic")
    val groupId = config.getString("kafka.group-id")

    val consumerSettings =
      ConsumerSettings(system.classicSystem, new StringDeserializer, new StringDeserializer)
        .withBootstrapServers(bootstrapServers)
        .withGroupId(groupId)
        .withProperty("auto.offset.reset", "earliest")

    val mat = SystemMaterializer(system).materializer

    logger.info(s"Starting Kafka consumer for topic=$topic group=$groupId")

    RestartSource.onFailuresWithBackoff(
      RestartSettings(3.seconds, 30.seconds, 0.2)
    ) { () =>
      Consumer
        .committableSource(consumerSettings, Subscriptions.topics(topic))
        .mapAsync(1) { msg =>

          // ---- FIXED JSON PARSING ----
          val js: JsValue =
            try Json.parse(msg.record.value())
            catch {
              case ex: Exception =>
                logger.error(s"Invalid JSON: ${msg.record.value()} ex=${ex.getMessage}")
                Json.obj("error" -> "invalid-json")
            }

          supervisorSystem ! NotificationSupervisor.IncomingEvent(js)

          msg.committableOffset.commitScaladsl().map(_ => ())
        }
    }.runWith(Sink.ignore)(mat)
  }
}

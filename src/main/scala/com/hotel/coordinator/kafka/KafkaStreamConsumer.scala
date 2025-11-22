package com.hotel.coordinator.kafka

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{RestartSource, Sink}
import akka.stream.{RestartSettings, Materializer}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.libs.json._
import scala.concurrent.duration._
import org.slf4j.LoggerFactory
import com.hotel.coordinator.actors.NotificationSupervisor

/**
 * KafkaStreamConsumer
 *
 * This component starts a Kafka consumer using Akka Streams (Classic Akka)
 * and forwards every consumed JSON message to the NotificationSupervisor actor.
 *
 * Features:
 *  - Connects to Kafka using bootstrap servers, topic, and groupId from config.
 *  - Uses RestartSource to auto-restart on failures.
 *  - Parses each Kafka message as JSON.
 *  - Sends parsed events to NotificationSupervisor.IncomingEvent.
 *  - Commits Kafka offsets after successful processing.
 */
object KafkaStreamConsumer {

  private val logger = LoggerFactory.getLogger("KafkaStreamConsumer")

  /**
   * Starts the Kafka stream consumer.
   *
   * @param supervisor  ActorRef to receive incoming JSON events.
   * @param system      Classic ActorSystem used to run Akka Streams and consumer.
   */
  def start(supervisor: ActorRef, system: ActorSystem): Unit = {

    implicit val sys: ActorSystem = system
    implicit val ec = system.dispatcher
    implicit val mat: Materializer = Materializer(system)

    val config = system.settings.config

    val bootstrapServers = config.getString("kafka.bootstrap-servers")
    val topic = config.getString("kafka.topic")
    val groupId = config.getString("kafka.group-id")

    val consumerSettings =
      ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
        .withBootstrapServers(bootstrapServers)
        .withGroupId(groupId)
        .withProperty("auto.offset.reset", "earliest")

    logger.info(s"Starting Kafka consumer for topic=$topic group=$groupId")

    RestartSource.onFailuresWithBackoff(
        RestartSettings(
          minBackoff = 3.seconds,
          maxBackoff = 30.seconds,
          randomFactor = 0.2
        )
      ) { () =>

        Consumer
          .committableSource(consumerSettings, Subscriptions.topics(topic))
          .mapAsync(1) { msg =>

            // Parse JSON
            val js: JsValue =
              try Json.parse(msg.record.value())
              catch {
                case ex: Exception =>
                  logger.error(s"Invalid JSON: ${msg.record.value()} ex=${ex.getMessage}")
                  Json.obj("error" -> "invalid-json")
              }

            // Send event to Supervisor (Classic)
            supervisor ! NotificationSupervisor.IncomingEvent(js)

            // Commit offset
            msg.committableOffset.commitScaladsl().map(_ => ())
          }
      }
      .runWith(Sink.ignore)

  }
}

package com.hotel.coordinator

import akka.actor.{ActorSystem, Props}
import com.hotel.coordinator.actors.NotificationSupervisor
import com.hotel.coordinator.kafka.KafkaStreamConsumer

object Main {
  def main(args: Array[String]): Unit = {
    println("Starting service-coordinator...")
    // create typed actor system
    implicit val system: ActorSystem =
      ActorSystem("service-coordinator-system")

    val emailService = new EmailService(system.settings.config)
    val supervisorRef = system.actorOf(NotificationSupervisor.props(emailService), "notification-supervisor")

    // start the kafka stream consumer which will send messages to NotificationSupervisor
    KafkaStreamConsumer.start(supervisorRef, system)

    // keep JVM alive until actor system terminated
    sys.addShutdownHook {
      system.log.info("Shutdown requested, terminating actor system...")
      system.terminate()
    }
  }
}

package com.hotel.coordinator

import akka.actor.typed.ActorSystem
import com.hotel.coordinator.actors.NotificationSupervisor
import com.hotel.coordinator.kafka.KafkaStreamConsumer

object Main {
  def main(args: Array[String]): Unit = {
    println("Starting service-coordinator...")
    // create typed actor system
    implicit val system: ActorSystem[NotificationSupervisor.Command] = ActorSystem(NotificationSupervisor(), "service-coordinator-system")

    // start the kafka stream consumer which will send messages to NotificationSupervisor
    KafkaStreamConsumer.start(system)

    // keep JVM alive until actor system terminated
    sys.addShutdownHook {
      system.log.info("Shutdown requested, terminating actor system...")
      system.terminate()
    }
  }
}

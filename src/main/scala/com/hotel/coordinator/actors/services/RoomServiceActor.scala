package com.hotel.coordinator.actors.services

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import play.api.libs.json.JsValue
import com.hotel.coordinator.EmailService
import org.slf4j.LoggerFactory

object RoomServiceActor {
  sealed trait Command
  final case class SendWelcome(payload: JsValue) extends Command
  final case class OnCheckedIn(payload: JsValue) extends Command
  final case class OnCheckedOut(payload: JsValue) extends Command

  def apply(emailService: EmailService): Behavior[Command] =
    Behaviors.setup { ctx =>
      val logger = LoggerFactory.getLogger("RoomServiceActor")
      Behaviors.receiveMessage {
        case SendWelcome(js) =>
          val guestEmail = (js \ "guest" \ "email").asOpt[String].getOrElse("")
          val body =
            s"""Welcome to the Hotel!
               |
               |Emergency contact: +91-111-222-333
               |Room service ext: 101
               |
               |Wishing you a pleasant stay.
               |""".stripMargin
          if (guestEmail.nonEmpty) {
            emailService.sendEmail(guestEmail, "Welcome to the Hotel", body)
            logger.info(s"RoomServiceActor: welcome email queued to $guestEmail")
          } else logger.warn("RoomServiceActor: guest email missing")
          Behaviors.same

        case OnCheckedIn(js) =>
          // optional: send additional checked-in notifications
          logger.info("RoomServiceActor: OnCheckedIn received")
          Behaviors.same

        case OnCheckedOut(js) =>
          // optional: cleanup
          logger.info("RoomServiceActor: OnCheckedOut received")
          Behaviors.same
      }
    }
}

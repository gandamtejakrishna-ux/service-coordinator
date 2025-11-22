package com.hotel.coordinator.actors.services

import akka.actor.{Actor, ActorLogging, Props}
import play.api.libs.json.JsValue
import com.hotel.coordinator.EmailService

class RoomServiceActor(emailService: EmailService)
  extends Actor with ActorLogging {

  import RoomServiceActor._

  /**
   * Handles incoming Room Service commands.
   *
   * Supported messages:
   *  - SendWelcome: send welcome email to guest.
   *  - OnCheckedIn: log check-in event.
   *  - OnCheckedOut: log check-out event.
   */
  override def receive: Receive = {

    
    // SEND WELCOME EMAIL
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
        log.info(s"RoomServiceActor: welcome email queued to $guestEmail")
      } else {
        log.warning("RoomServiceActor: guest email missing")
      }

    
    // CHECKED-IN EVENT
    case OnCheckedIn(_) =>
      log.info("RoomServiceActor: OnCheckedIn received")

    
    // CHECKED-OUT EVENT
    case OnCheckedOut(_) =>
      log.info("RoomServiceActor: OnCheckedOut received")
  }
}

object RoomServiceActor {

  /**
   * Base trait for all Room Service commands.
   */
  sealed trait Command
  final case class SendWelcome(payload: JsValue) extends Command
  final case class OnCheckedIn(payload: JsValue) extends Command
  final case class OnCheckedOut(payload: JsValue) extends Command

  /**
   * Create Props for RoomServiceActor.
   */
  def props(emailService: EmailService): Props =
    Props(new RoomServiceActor(emailService))
}

package com.hotel.coordinator.actors.services

import akka.actor.{Actor, ActorLogging, Props}
import play.api.libs.json.JsValue
import com.hotel.coordinator.EmailService
import scala.util.Random

class WifiServiceActor(emailService: EmailService)
  extends Actor with ActorLogging {

  import WifiServiceActor._

  private def genPassword(): String = {
    val r = new Random()
    "WIFI-" + r.alphanumeric.filter(_.isLetterOrDigit).take(8).mkString
  }

  override def receive: Receive = {

    // SEND WIFI CREDENTIALS EMAIL
    case SendWifi(js) =>
      val guestEmail = (js \ "guest" \ "email").asOpt[String].getOrElse("")

      if (guestEmail.nonEmpty) {
        val password = genPassword()

        val body =
          s"""Hello,
             |
             |Your WiFi credentials:
             |SSID: HOTEL_GUEST
             |Password: $password
             |
             |(Valid until check-out)
             |""".stripMargin

        emailService.sendEmail(guestEmail, "Your WiFi credentials", body)
        log.info(s"WifiServiceActor: WiFi email sent to $guestEmail")

      } else {
        log.warning("WifiServiceActor: guest email missing")
      }

    
    // CHECKED-OUT (optional action)
    
    case OnCheckedOut(_) =>
      log.info("WifiServiceActor: OnCheckedOut - could revoke credentials")
    // no functional change
  }
}

object WifiServiceActor {

  sealed trait Command
  final case class SendWifi(payload: JsValue) extends Command
  final case class OnCheckedOut(payload: JsValue) extends Command

  def props(emailService: EmailService): Props =
    Props(new WifiServiceActor(emailService))
}

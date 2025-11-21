package com.hotel.coordinator.actors.services

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import play.api.libs.json.JsValue
import com.hotel.coordinator.EmailService
import org.slf4j.LoggerFactory
import scala.util.Random

object WifiServiceActor {
  sealed trait Command
  final case class SendWifi(payload: JsValue) extends Command
  final case class OnCheckedOut(payload: JsValue) extends Command

  def apply(emailService: EmailService): Behavior[Command] =
    Behaviors.setup { ctx =>
      val logger = LoggerFactory.getLogger("WifiServiceActor")

      def genPassword(): String = {
        val r = new Random()
        "WIFI-" + r.alphanumeric.filter(_.isLetterOrDigit).take(8).mkString
      }

      Behaviors.receiveMessage {
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
            logger.info(s"WifiServiceActor: WiFi email sent to $guestEmail")
          } else logger.warn("WifiServiceActor: guest email missing")
          Behaviors.same

        case OnCheckedOut(js) =>
          logger.info("WifiServiceActor: OnCheckedOut - could revoke credentials")
          // integrate with WiFi infra to revoke if needed
          Behaviors.same
      }
    }
}

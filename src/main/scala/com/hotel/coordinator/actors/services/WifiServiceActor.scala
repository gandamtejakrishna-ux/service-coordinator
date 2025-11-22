package com.hotel.coordinator.actors.services

import akka.actor.{Actor, ActorLogging, Props}
import play.api.libs.json.JsValue
import com.hotel.coordinator.EmailService
import scala.util.Random

/**
 * Actor responsible for handling WiFi-related guest notifications.
 *
 * <p>This actor performs two main tasks:</p>
 * <ul>
 *   <li>Sends WiFi login credentials to the guest when a CHECKED_IN event arrives.</li>
 *   <li(Optional) Handles CHECKED_OUT events for future actions like credential revocation.</li>
 * </ul>
 *
 * @param emailService service used to send email notifications
 */
class WifiServiceActor(emailService: EmailService)
  extends Actor with ActorLogging {

  import WifiServiceActor._

  private def genPassword(): String = {
    val r = new Random()
    "WIFI-" + r.alphanumeric.filter(_.isLetterOrDigit).take(8).mkString
  }

  override def receive: Receive = {

    /**
     * Generates and sends WiFi credentials to the guest's email.
     *
     * @param payload JSON containing guest details (expects guest.email)
     */
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


    /**
     * Triggered when the guest checks out.
     * Currently logs an info message and performs no functional change.
     *
     * @param payload JSON containing bookingId or guest info
     */
    case OnCheckedOut(_) =>
      log.info("WifiServiceActor: OnCheckedOut - could revoke credentials")
    // no functional change
  }
}

object WifiServiceActor {

  sealed trait Command
  final case class SendWifi(payload: JsValue) extends Command
  final case class OnCheckedOut(payload: JsValue) extends Command

  /**
   * Creates Props used for actor creation.
   *
   * @param emailService injected email service
   * @return Props for WifiServiceActor
   */
  def props(emailService: EmailService): Props =
    Props(new WifiServiceActor(emailService))
}

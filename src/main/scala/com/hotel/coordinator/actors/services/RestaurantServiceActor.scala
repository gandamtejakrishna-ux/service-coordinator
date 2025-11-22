package com.hotel.coordinator.actors.services

import akka.actor.{Actor, ActorLogging, Props, Cancellable}
import play.api.libs.json.JsValue
import com.hotel.coordinator.EmailService
import scala.concurrent.duration._
import scala.collection.mutable

/**
 * Actor responsible for handling restaurant-related notifications for guests.
 *
 * <p>This actor schedules and sends automated daily menu emails to guests
 * while they stay in the hotel. When the guest checks out, the scheduled
 * menu emails are cancelled automatically.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Schedules repeating daily menu emails using Akka scheduler.</li>
 *   <li>Cancels the email schedule on guest check-out.</li>
 *   <li>Uses EmailService to send emails in either console or SMTP mode.</li>
 * </ul>
 *
 * @param emailService  Service used to send actual emails
 * @param initialDelay  Delay before the first menu email is sent
 * @param interval      Interval between subsequent menu emails
 */
class RestaurantServiceActor(emailService: EmailService,
                             initialDelay: FiniteDuration,
                             interval: FiniteDuration)
  extends Actor with ActorLogging {

  import RestaurantServiceActor._
  import context.dispatcher   // ExecutionContext for scheduler

  /** Stores active schedules mapped by bookingId */
  private val schedules = mutable.Map.empty[String, Cancellable]

  /**
   * Handles incoming messages for scheduling or cancelling menu emails.
   */
  override def receive: Receive = {


    /**
     * Schedules a repeating daily menu email for the given booking.
     * Extracts guest email and bookingId from the JSON payload.
     */
    case ScheduleMenu(js) =>
      val bookingId = (js \ "bookingId").asOpt[String].getOrElse {
        val gen = java.util.UUID.randomUUID().toString
        log.warning(s"ScheduleMenu: bookingId missing, generated $gen")
        gen
      }

      val guestEmail = (js \ "guest" \ "email").asOpt[String].getOrElse("")

      if (guestEmail.nonEmpty && !schedules.contains(bookingId)) {

        val cancellable =
          context.system.scheduler.scheduleAtFixedRate(initialDelay, interval) { () =>
            val body = "Today's menu: Breakfast 7-10, Lunch 12-3, Dinner 7-10"
            emailService.sendEmail(guestEmail, "Daily Menu", body)
            log.info(s"RestaurantServiceActor: sent daily menu to $guestEmail for booking $bookingId")
          }

        schedules.put(bookingId, cancellable)
        log.info(s"RestaurantServiceActor: scheduled menu for booking=$bookingId")

      } else {
        log.warning(
          s"RestaurantServiceActor: cannot schedule menu for booking=$bookingId " +
            s"(missing email or already scheduled)"
        )
      }


    /**
     * Cancels scheduled daily emails when guest checks out.
     */
    case CancelMenu(js) =>
      val bookingId = (js \ "bookingId").asOpt[String].getOrElse("")

      schedules.remove(bookingId).foreach { c =>
        c.cancel()
        log.info(s"RestaurantServiceActor: cancelled schedule for booking=$bookingId")
      }


    /**
     * Internal message (not used in Classic version).
     */
    case SendMenu(_) =>
      // Not used in classic version
      ()
  }
}

object RestaurantServiceActor {

  sealed trait Command
  final case class ScheduleMenu(payload: JsValue) extends Command
  final case class CancelMenu(payload: JsValue) extends Command
  final case class SendMenu(bookingId: String) extends Command

  /**
   * Creates Props for RestaurantServiceActor.
   */
  def props(emailService: EmailService,
            initialDelay: FiniteDuration,
            interval: FiniteDuration): Props =
    Props(new RestaurantServiceActor(emailService, initialDelay, interval))
}

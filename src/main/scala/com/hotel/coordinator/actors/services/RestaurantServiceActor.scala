package com.hotel.coordinator.actors.services

import akka.actor.{Actor, ActorLogging, Props, Cancellable}
import play.api.libs.json.JsValue
import com.hotel.coordinator.EmailService
import scala.concurrent.duration._
import scala.collection.mutable

class RestaurantServiceActor(emailService: EmailService,
                             initialDelay: FiniteDuration,
                             interval: FiniteDuration)
  extends Actor with ActorLogging {

  import RestaurantServiceActor._
  import context.dispatcher   // ExecutionContext for scheduler

  // bookingId -> scheduler handle
  private val schedules = mutable.Map.empty[String, Cancellable]

  override def receive: Receive = {

    
    // SCHEDULE DAILY MENU EMAIL
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

    
    // CANCEL DAILY MENU EMAIL
    case CancelMenu(js) =>
      val bookingId = (js \ "bookingId").asOpt[String].getOrElse("")

      schedules.remove(bookingId).foreach { c =>
        c.cancel()
        log.info(s"RestaurantServiceActor: cancelled schedule for booking=$bookingId")
      }

    
    // INTERNAL MESSAGE (unused but kept for compatibility)
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

  def props(emailService: EmailService,
            initialDelay: FiniteDuration,
            interval: FiniteDuration): Props =
    Props(new RestaurantServiceActor(emailService, initialDelay, interval))
}

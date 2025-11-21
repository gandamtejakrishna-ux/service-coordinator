package com.hotel.coordinator.actors.services

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import play.api.libs.json.JsValue
import com.hotel.coordinator.EmailService
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import akka.actor.Cancellable
import scala.collection.mutable

object RestaurantServiceActor {
  sealed trait Command
  final case class ScheduleMenu(payload: JsValue) extends Command
  final case class CancelMenu(payload: JsValue) extends Command
  private final case class SendMenu(bookingId: String) extends Command

  def apply(emailService: EmailService, initialDelay: FiniteDuration, interval: FiniteDuration): Behavior[Command] =
    Behaviors.setup { ctx =>
      val logger = LoggerFactory.getLogger("RestaurantServiceActor")
      implicit val system = ctx.system
      implicit val ec = ctx.executionContext

      // bookingId -> Cancellable
      val schedules = mutable.Map.empty[String, Cancellable]

      Behaviors.receiveMessage {
        case ScheduleMenu(js) =>
          val bookingId = (js \ "bookingId").asOpt[String].getOrElse {
            val gen = java.util.UUID.randomUUID().toString
            logger.warn("ScheduleMenu: bookingId missing, generated " + gen); gen
          }
          val guestEmail = (js \ "guest" \ "email").asOpt[String].getOrElse("")
          if (guestEmail.nonEmpty && !schedules.contains(bookingId)) {
            // schedule repeating job
            val cancellable = system.scheduler.scheduleAtFixedRate(initialDelay, interval) { () =>
              // send daily menu
              val body = "Today's menu: Breakfast 7-10, Lunch 12-3, Dinner 7-10"
              emailService.sendEmail(guestEmail, "Daily Menu", body)
              logger.info(s"RestaurantServiceActor: sent daily menu to $guestEmail for booking $bookingId")
            }(ec)
            schedules.put(bookingId, cancellable)
            logger.info(s"RestaurantServiceActor: scheduled menu for booking=$bookingId")
          } else {
            logger.warn(s"RestaurantServiceActor: cannot schedule menu for booking=$bookingId (missing email or already scheduled)")
          }
          Behaviors.same

        case CancelMenu(js) =>
          val bookingId = (js \ "bookingId").asOpt[String].getOrElse("")
          schedules.remove(bookingId).foreach { c =>
            c.cancel()
            logger.info(s"RestaurantServiceActor: cancelled schedule for booking=$bookingId")
          }
          Behaviors.same

        case SendMenu(bookingId) =>
          // keep for internal use if needed
          Behaviors.unhandled
      }
    }
}

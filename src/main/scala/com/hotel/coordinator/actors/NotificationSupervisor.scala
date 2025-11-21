package com.hotel.coordinator.actors

import akka.actor.typed.{Behavior}
import akka.actor.typed.scaladsl.Behaviors
import play.api.libs.json.JsValue
import com.hotel.coordinator.actors.services._
import com.hotel.coordinator.EmailService
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

object NotificationSupervisor {
  sealed trait Command
  final case class IncomingEvent(payload: JsValue) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      val cfg = context.system.settings.config
      val emailService = new EmailService(context.system.settings.config)

      val roomService = context.spawn(RoomServiceActor(emailService), "room-service")
      val wifiService = context.spawn(WifiServiceActor(emailService), "wifi-service")
      val bookingReminder = context.spawn(BookingReminderActor(emailService), "booking-reminders")


      // convert config to FiniteDuration
      import scala.concurrent.duration._
      val initialDelay = Duration(cfg.getString("restaurant.daily-initial-delay")).asInstanceOf[FiniteDuration]
      val interval = Duration(cfg.getString("restaurant.daily-interval")).asInstanceOf[FiniteDuration]

      val restaurantService = context.spawn(
        RestaurantServiceActor(emailService, initialDelay, interval),
        "restaurant-service"
      )

      Behaviors.receiveMessage {
        case IncomingEvent(js) =>
          val evtType = (js \ "eventType").asOpt[String]
            .orElse((js \ "event_type").asOpt[String])
            .map(_.toUpperCase)
            .getOrElse("UNKNOWN")

          evtType match {
            case "BOOKING_CREATED" =>
              bookingReminder ! BookingReminderActor.ScheduleCheckInReminder(js)
              bookingReminder ! BookingReminderActor.ScheduleCheckOutReminder(js)



            case "CHECKED_IN" =>
              roomService ! RoomServiceActor.SendWelcome(js)
              wifiService ! WifiServiceActor.SendWifi(js)
              restaurantService ! RestaurantServiceActor.ScheduleMenu(js)
              roomService ! RoomServiceActor.OnCheckedIn(js)


            case "CHECKED_OUT" =>
              restaurantService ! RestaurantServiceActor.CancelMenu(js)
              roomService ! RoomServiceActor.OnCheckedOut(js)
              wifiService ! WifiServiceActor.OnCheckedOut(js)
              bookingReminder ! BookingReminderActor.CancelReminders(js)


            case other =>
              context.log.warn(s"NotificationSupervisor: unknown eventType=$other")
          }
          Behaviors.same
      }
    }
}

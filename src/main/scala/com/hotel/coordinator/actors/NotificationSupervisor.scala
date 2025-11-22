package com.hotel.coordinator.actors

import akka.actor._
import play.api.libs.json.JsValue
import com.hotel.coordinator.EmailService
import com.hotel.coordinator.actors.services._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

object NotificationSupervisor {
  // ONE definition (shared everywhere)
  case class IncomingEvent(payload: JsValue)

  def props(emailService: EmailService): Props =
    Props(new NotificationSupervisor(emailService))
}

class NotificationSupervisor(emailService: EmailService)
  extends Actor
    with ActorLogging {

  import NotificationSupervisor._

  //  Child actors 
  private val roomService =
    context.actorOf(RoomServiceActor.props(emailService), "room-service")

  private val wifiService =
    context.actorOf(WifiServiceActor.props(emailService), "wifi-service")

  private val bookingReminder =
    context.actorOf(BookingReminderActor.props(emailService), "booking-reminders")

  // Restaurant config
  private val cfg = ConfigFactory.load()
  private val initialDelay = Duration(cfg.getString("restaurant.daily-initial-delay")).asInstanceOf[FiniteDuration]
  private val interval     = Duration(cfg.getString("restaurant.daily-interval")).asInstanceOf[FiniteDuration]

  private val restaurantService =
    context.actorOf(RestaurantServiceActor.props(emailService, initialDelay, interval), "restaurant-service")

  override def receive: Receive = {

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
          log.warning(s"NotificationSupervisor: unknown eventType=$other")
      }
  }
}

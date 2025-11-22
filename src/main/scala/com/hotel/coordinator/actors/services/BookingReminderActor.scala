package com.hotel.coordinator.actors.services

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import play.api.libs.json.JsValue
import scala.concurrent.duration._
import scala.collection.mutable
import com.hotel.coordinator.EmailService

object BookingReminderActor {
  final case class ScheduleCheckInReminder(payload: JsValue)
  final case class ScheduleCheckOutReminder(payload: JsValue)
  final case class CancelReminders(payload: JsValue)

  // Internal messages (sent to self by scheduler)
  private final case class SendCheckInEmail(email: String, bookingId: String)
  private final case class SendCheckOutEmail(email: String, bookingId: String)

  def props(emailService: EmailService): Props = Props(new BookingReminderActor(emailService))
}

class BookingReminderActor(emailService: EmailService) extends Actor with ActorLogging {
  import BookingReminderActor._
  import context.dispatcher

  // bookingId -> Cancellable (so we can cancel scheduled jobs)
  private val checkInTimers = mutable.Map.empty[String, Cancellable]
  private val checkOutTimers = mutable.Map.empty[String, Cancellable]

  override def receive: Receive = {

    
    // Schedule Check-In Reminder (30 mins before 11 AM)
    case ScheduleCheckInReminder(js) =>
      val bookingId = (js \ "bookingId").as[String]
      val email     = (js \ "guest" \ "email").as[String]

      log.info(s"[Reminder] Scheduling check-in reminder in 1 minute → $email (booking=$bookingId)")

      val cancellable =
        context.system.scheduler.scheduleOnce(1.minute) {
          self ! SendCheckInEmail(email, bookingId)
        }

      checkInTimers += bookingId -> cancellable
      log.info(s"[Reminder] Check-in reminder scheduled.")


    // Schedule Check-Out Reminder (30 mins before 11 AM)
    case ScheduleCheckOutReminder(js) =>
      val bookingId = (js \ "bookingId").as[String]
      val email = (js \ "guest" \ "email").as[String]
      val date = (js \ "checkOutDate").as[String]

      val target = java.time.LocalDateTime.parse(s"${date}T11:00:00")
      val reminder = target.minusMinutes(30)

      val millis = java.time.Duration
        .between(java.time.LocalDateTime.now(), reminder)
        .toMillis

      if (millis > 0) {
        val delay = millis.millis
        log.info(s"[Reminder] Scheduling check-out reminder for $email at $reminder (booking=$bookingId)")

        val cancellable = context.system.scheduler.scheduleOnce(delay) {
          self ! SendCheckOutEmail(email, bookingId)
        }

        checkOutTimers += bookingId -> cancellable
      } else {
        log.info(s"[Reminder] Skipping scheduling past check-out reminder for booking=$bookingId reminder=$reminder")
      }

    // -----------------------------
    // Cancel all reminders
    // -----------------------------
    case CancelReminders(js) =>
      val bookingId = (js \ "bookingId").as[String]

      checkInTimers.remove(bookingId).foreach { c =>
        c.cancel()
        log.info(s"[Reminder] Cancelled check-in timer for $bookingId")
      }

      checkOutTimers.remove(bookingId).foreach { c =>
        c.cancel()
        log.info(s"[Reminder] Cancelled check-out timer for $bookingId")
      }

    // -----------------------------
    // Actual Reminder Email: Check-in
    // -----------------------------
    case SendCheckInEmail(email, bookingId) =>
      val subject = "Your Check-In Time is in 30 Minutes"
      val body =
        s"""
           |Dear Guest,
           |
           |This is a reminder that your check-in time is in 30 minutes.
           |
           |Booking ID: $bookingId
           |Scheduled Check-In Time: 11:00 AM
           |
           |Regards,
           |Hotel Reception
           |""".stripMargin

      emailService.sendEmail(email, subject, body)

    // -----------------------------
    // Actual Reminder Email: Check-out
    // -----------------------------
    case SendCheckOutEmail(email, bookingId) =>
      val subject = "Your Check-Out Time is in 30 Minutes"
      val body =
        s"""
           |Reminder:
           |Your check-out time is in 30 minutes.
           |
           |Booking ID: $bookingId
           |Scheduled Check-Out Time: 11:00 AM
           |
           |Thank you for staying with us!
           |""".stripMargin

      emailService.sendEmail(email, subject, body)

    case other =>
      log.warning(s"BookingReminderActor: unknown message $other")
  }

  override def postStop(): Unit = {
    // safety: cancel any outstanding scheduled tasks when actor stops
    checkInTimers.values.foreach(_.cancel())
    checkOutTimers.values.foreach(_.cancel())
    super.postStop()
  }
}

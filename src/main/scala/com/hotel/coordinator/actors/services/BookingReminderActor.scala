//package com.hotel.coordinator.actors.services
//
//import akka.actor.typed.{Behavior}
//import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
//import play.api.libs.json.JsValue
//import scala.concurrent.duration._
//import com.hotel.coordinator.EmailService
//
//object BookingReminderActor {
//
//  sealed trait Command
//  final case class ScheduleCheckInReminder(payload: JsValue) extends Command
//  final case class ScheduleCheckOutReminder(payload: JsValue) extends Command
//  final case class CancelReminders(payload: JsValue) extends Command
//
//  private case class SendCheckInEmail(email: String, bookingId: String) extends Command
//  private case class SendCheckOutEmail(email: String, bookingId: String) extends Command
//
//  def apply(emailService: EmailService): Behavior[Command] =
//    Behaviors.withTimers { timers =>
//      Behaviors.setup { context =>
//
//        var checkInTimers = Map.empty[String, akka.actor.Cancellable]
//        var checkOutTimers = Map.empty[String, akka.actor.Cancellable]
//
//        Behaviors.receiveMessage {
//
//          // -------------------------------------------------
//          // Schedule Check-In Reminder (30 mins before 11 AM)
//          // -------------------------------------------------
//          case ScheduleCheckInReminder(js) =>
//            val bookingId = (js \ "bookingId").as[String]
//            val email = (js \ "guest" \ "email").as[String]
//            val date = (js \ "checkInDate").as[String]
//
//            val target = java.time.LocalDateTime.parse(s"${date}T11:00:00")
//            val reminder = target.minusMinutes(30)
//
//            val millis = java.time.Duration
//              .between(java.time.LocalDateTime.now(), reminder)
//              .toMillis
//
//            if (millis > 0) {
//              val delay = millis.millis
//
//              context.log.info(s"[Reminder] Scheduling check-in reminder for $email at $reminder")
//
//              val c = timers.startSingleTimer(
//                s"checkin-$bookingId",
//                SendCheckInEmail(email, bookingId),
//                delay
//              )
//
//              checkInTimers += bookingId -> c
//            }
//
//            Behaviors.same
//
//          // -------------------------------------------------
//          // Schedule Check-Out Reminder (30 mins before 11 AM)
//          // -------------------------------------------------
//          case ScheduleCheckOutReminder(js) =>
//            val bookingId = (js \ "bookingId").as[String]
//            val email = (js \ "guest" \ "email").as[String]
//            val date = (js \ "checkOutDate").as[String]
//
//            val target = java.time.LocalDateTime.parse(s"${date}T11:00:00")
//            val reminder = target.minusMinutes(30)
//
//            val millis = java.time.Duration
//              .between(java.time.LocalDateTime.now(), reminder)
//              .toMillis
//
//            if (millis > 0) {
//              val delay = millis.millis
//
//              context.log.info(s"[Reminder] Scheduling check-out reminder for $email at $reminder")
//
//              val c = timers.startSingleTimer(
//                s"checkout-$bookingId",
//                SendCheckOutEmail(email, bookingId),
//                delay
//              )
//
//              checkOutTimers += bookingId -> c
//            }
//
//            Behaviors.same
//
//          // -----------------------------
//          // Cancel all reminders
//          // -----------------------------
//          case CancelReminders(js) =>
//            val bookingId = (js \ "bookingId").as[String]
//
//            checkInTimers.get(bookingId).foreach(_.cancel())
//            checkOutTimers.get(bookingId).foreach(_.cancel())
//
//            checkInTimers -= bookingId
//            checkOutTimers -= bookingId
//
//            context.log.info(s"[Reminder] Cancelled reminders for $bookingId")
//
//            Behaviors.same
//
//          // -----------------------------
//          // Actual Reminder Email: Check-in
//          // -----------------------------
//          case SendCheckInEmail(email, bookingId) =>
//            val subject = "Your Check-In Time is in 30 Minutes"
//            val body =
//              s"""
//                 |Dear Guest,
//                 |
//                 |This is a reminder that your check-in time is in **30 minutes**.
//                 |
//                 |Booking ID: $bookingId
//                 |Scheduled Check-In Time: 11:00 AM
//                 |
//                 |Regards,
//                 |Hotel Reception
//                 |""".stripMargin
//
//            emailService.sendEmail(email, subject, body)
//            Behaviors.same
//
//          // -----------------------------
//          // Actual Reminder Email: Check-out
//          // -----------------------------
//          case SendCheckOutEmail(email, bookingId) =>
//            val subject = "Your Check-Out Time is in 30 Minutes"
//            val body =
//              s"""
//                 |Reminder:
//                 |Your check-out time is in **30 minutes**.
//                 |
//                 |Booking ID: $bookingId
//                 |Scheduled Check-Out Time: 11:00 AM
//                 |
//                 |Thank you for staying with us!
//                 |""".stripMargin
//
//            emailService.sendEmail(email, subject, body)
//            Behaviors.same
//        }
//      }
//    }
//}

package com.hotel.coordinator.actors.services

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import play.api.libs.json.JsValue
import scala.concurrent.duration._
import scala.collection.mutable
import com.hotel.coordinator.EmailService

object BookingReminderActor {

  sealed trait Command
  final case class ScheduleCheckInReminder(payload: JsValue) extends Command
  final case class ScheduleCheckOutReminder(payload: JsValue) extends Command
  final case class CancelReminders(payload: JsValue) extends Command

  private case class SendCheckInEmail(email: String, bookingId: String) extends Command
  private case class SendCheckOutEmail(email: String, bookingId: String) extends Command

  def apply(emailService: EmailService): Behavior[Command] =
    Behaviors.withTimers { timers: TimerScheduler[Command] =>
      Behaviors.setup { context =>

        // bookingId -> timerKey
        val checkInTimers = mutable.Map.empty[String, String]
        val checkOutTimers = mutable.Map.empty[String, String]

        Behaviors.receiveMessage {

          // -------------------------------------------------
          // Schedule Check-In Reminder (30 mins before 11 AM)
          // -------------------------------------------------
          case ScheduleCheckInReminder(js) =>
            val bookingId = (js \ "bookingId").as[String]
            val email = (js \ "guest" \ "email").as[String]
            val date = (js \ "checkInDate").as[String]

            val target = java.time.LocalDateTime.parse(s"${date}T11:00:00")
            val reminder = target.minusMinutes(30)

            val millis = java.time.Duration
              .between(java.time.LocalDateTime.now(), reminder)
              .toMillis

            if (millis > 0) {
              val delay = millis.millis

              context.log.info(s"[Reminder] Scheduling check-in reminder for $email at $reminder")

              val key = s"checkin-$bookingId"

              // startSingleTimer returns Unit — we store the key for later cancellation
              timers.startSingleTimer(
                key,
                SendCheckInEmail(email, bookingId),
                delay
              )

              checkInTimers += bookingId -> key
            } else {
              context.log.info(s"[Reminder] Skipping scheduling past reminder for booking=$bookingId reminder=$reminder")
            }

            Behaviors.same

          // -------------------------------------------------
          // Schedule Check-Out Reminder (30 mins before 11 AM)
          // -------------------------------------------------
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

              context.log.info(s"[Reminder] Scheduling check-out reminder for $email at $reminder")

              val key = s"checkout-$bookingId"

              timers.startSingleTimer(
                key,
                SendCheckOutEmail(email, bookingId),
                delay
              )

              checkOutTimers += bookingId -> key
            } else {
              context.log.info(s"[Reminder] Skipping scheduling past reminder for booking=$bookingId reminder=$reminder")
            }

            Behaviors.same

          // -----------------------------
          // Cancel all reminders
          // -----------------------------
          case CancelReminders(js) =>
            val bookingId = (js \ "bookingId").as[String]

            checkInTimers.get(bookingId).foreach { key =>
              timers.cancel(key)
              checkInTimers -= bookingId
              context.log.info(s"[Reminder] Cancelled check-in timer for $bookingId (key=$key)")
            }

            checkOutTimers.get(bookingId).foreach { key =>
              timers.cancel(key)
              checkOutTimers -= bookingId
              context.log.info(s"[Reminder] Cancelled check-out timer for $bookingId (key=$key)")
            }

            Behaviors.same

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

            // fire-and-forget — EmailService handles logging/errors
            emailService.sendEmail(email, subject, body)
            Behaviors.same

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
            Behaviors.same
        }
      }
    }
}

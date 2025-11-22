package com.hotel.coordinator

import com.typesafe.config.Config
import jakarta.mail._
import jakarta.mail.internet._

import java.util.Properties

/**
 * EmailService handles all outgoing emails for the service-coordinator.
 *
 * It supports two modes:
 *  - console : prints emails to the console (useful for development/testing)
 *  - smtp    : sends real emails using SMTP server credentials
 *
 * SMTP settings (host, port, username, password) are loaded from application.conf.
 *
 * @param config Application configuration used to read email settings.
 */
class EmailService(config: Config) {

  private val mode = config.getString("email.mode")
  private val from = config.getString("email.from")

  private val smtpHost = config.getString("email.smtp.host")
  private val smtpPort = config.getInt("email.smtp.port")
  private val smtpUser = config.getString("email.smtp.user")
  private val smtpPass = config.getString("email.smtp.pass")

  /**
   * Sends an email to the given recipient using either console or SMTP mode.
   *
   * @param to Recipient email address
   * @param subject Email subject
   * @param body Email content text
   */
  def sendEmail(to: String, subject: String, body: String): Unit = {
    mode match {
      case "console" =>
        println(
          s"""
             |--- EMAIL (console) ---
             |From: $from
             |To: $to
             |Subject: $subject
             |Body:
             |$body
             |-----------------------
             |""".stripMargin)

      case "smtp" =>
        val props = new Properties()
        props.put("mail.smtp.auth", "true")
        props.put("mail.smtp.starttls.enable", "true")
        props.put("mail.smtp.host", smtpHost)
        props.put("mail.smtp.port", smtpPort.toString)

        val session = Session.getInstance(
          props,
          new Authenticator() {
            override protected def getPasswordAuthentication: PasswordAuthentication =
              new PasswordAuthentication(smtpUser, smtpPass)
          }
        )

        try {
          val msg = new MimeMessage(session)
          msg.setFrom(new InternetAddress(from))
          msg.setRecipients(Message.RecipientType.TO, to)
          msg.setSubject(subject)
          msg.setText(body)

          Transport.send(msg)

          println(s"[EmailService] SMTP email sent → $to")

        } catch {
          case ex: Exception =>
            println(s"[EmailService] SMTP send failed: ${ex.getMessage}")
            throw ex
        }

      case other =>
        println(s"[EmailService] Unknown email mode=$other (printing only)")
        println(s"To: $to\nSubject: $subject\nBody:\n$body")
    }
  }
}

package com.hotel.coordinator

import com.typesafe.config.Config
import jakarta.mail._
import jakarta.mail.internet._

import java.util.Properties

class EmailService(config: Config) {

  private val mode = config.getString("email.mode")
  private val from = config.getString("email.from")

  private val smtpHost = config.getString("email.smtp.host")
  private val smtpPort = config.getInt("email.smtp.port")
  private val smtpUser = config.getString("email.smtp.user")
  private val smtpPass = config.getString("email.smtp.pass")

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

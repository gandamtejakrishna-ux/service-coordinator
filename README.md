# Service Coordinator

This microservice listens to Kafka events from the Booking Service and sends:
- Welcome email (Room Service)
- WiFi credentials email (WiFi Service)
- Daily restaurant menu email
- Check-in & Check-out reminders
- Cancels all schedules on checkout

The service uses:
- Akka Actors
- Kafka Consumer
- SMTP Email (Gmail App Password)
- Database (for schedules & notification logs)

---

## Port
Runs on:
http://localhost:9003

---

## Prerequisites
- Java 17+
- sbt
- MySQL running and configured
- Kafka broker running (local or Docker)
- Booking Service running on port **9002**
- Gmail App Password for sending emails

---

## How to Run
Inside the `service-coordinator` folder: sbt run

Coordinator will automatically:
- Start Kafka consumer
- Process events from outbox
- Schedule reminders + restaurant menu
- Send all required emails

---

## Postman Collection
Use the same collection since it includes booking + reception flows:

https://tejakrishna-g-9710885.postman.co/workspace/Teja-Krishna-Gandam's-Workspace~c2fcbc57-358c-43f1-8a7d-6f28c1e682e8/collection/50250192-d3286a09-6256-435a-a9a8-a98a7372f827?action=share&creator=50250192

---

## Project Tech Spec
https://docs.google.com/document/d/11bjs6H4MYlR1RKGjhPuceq4ceioiJBp_seJ1LzujyH4/edit
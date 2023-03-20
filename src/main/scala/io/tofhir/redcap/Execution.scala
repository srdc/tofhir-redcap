package io.tofhir.redcap

import akka.actor.ActorSystem

/**
 * Provides the Actor System for the project
 * */
object Execution {
  implicit lazy val actorSystem: ActorSystem = ActorSystem("toFHIR-REDCap")
}
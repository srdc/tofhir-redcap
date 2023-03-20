package io.tofhir.redcap

import io.tofhir.redcap.server.ToFhirRedCapServer

/**
 * Entrypoint of the application
 */
object Boot extends App {
  // start server
  ToFhirRedCapServer.start()
}
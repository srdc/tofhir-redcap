package io.tofhir.redcap.server

import io.tofhir.redcap.endpoint.ToFhirRedCapServerEndpoint
import io.tofhir.redcap.server.config.WebServerConfig

/**
 * Represents toFHIR-REDCap Server
 * */
object ToFhirRedCapServer {
  /**
   * Starts the server with given configurations.
   * */
  def start(): Unit = {
    import io.tofhir.redcap.Execution.actorSystem

    val webServerConfig = new WebServerConfig(actorSystem.settings.config.getConfig("webserver"))
    val endpoint = new ToFhirRedCapServerEndpoint(webServerConfig)
    // start http server
    ToFhirRedCapHttpServer.start(endpoint.toFHIRRoute, webServerConfig)
  }
}

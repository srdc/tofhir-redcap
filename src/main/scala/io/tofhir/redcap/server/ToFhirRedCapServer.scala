package io.tofhir.redcap.server

import io.tofhir.redcap.config.ToFhirRedCapConfig
import io.tofhir.redcap.endpoint.ToFhirRedCapServerEndpoint

/**
 * Represents toFHIR-REDCap Server
 * */
object ToFhirRedCapServer {
  /**
   * Starts the server with given configurations.
   * */
  def start(): Unit = {
    import io.tofhir.redcap.Execution.actorSystem

    val endpoint = new ToFhirRedCapServerEndpoint(ToFhirRedCapConfig.webServerConfig, ToFhirRedCapConfig.redCapConfig)
    // start http server
    ToFhirRedCapHttpServer.start(endpoint.toFHIRRoute, ToFhirRedCapConfig.webServerConfig)
  }
}

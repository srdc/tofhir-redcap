package io.tofhir.redcap.endpoint

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, Route}
import io.tofhir.redcap.server.config.WebServerConfig
import io.tofhir.redcap.server.interceptor.{ICORSHandler, IErrorHandler}

/**
 * Encapsulates all services and directives
 * Main Endpoint for toFHIR-RedCap server
 */
class ToFhirRedCapServerEndpoint(webServerConfig: WebServerConfig) extends ICORSHandler with IErrorHandler {

  val notificationEndpoint = new NotificationEndpoint()

  lazy val toFHIRRoute: Route =
    pathPrefix(webServerConfig.baseUri) {
      corsHandler {
        extractMethod { _ =>
          extractUri { _ =>
            extractRequestEntity { _ =>
              optionalHeaderValueByName("X-Correlation-Id") { _ =>
                handleRejections(RejectionHandler.default) { // Default rejection handling
                  handleExceptions(exceptionHandler()) { // Handle exceptions
                    notificationEndpoint.route()
                  }
                }
              }
            }
          }
        }
      }
    }
}

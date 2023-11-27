package io.tofhir.redcap.endpoint

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, Route}
import io.tofhir.redcap.config.RedCapConfig
import io.tofhir.redcap.server.config.WebServerConfig
import io.tofhir.redcap.server.interceptor.{ICORSHandler, IErrorHandler}
import io.tofhir.redcap.service.project.RedCapProjectConfigFileRepository

/**
 * Encapsulates all services and directives
 * Main Endpoint for toFHIR-RedCap server
 */
class ToFhirRedCapServerEndpoint(webServerConfig: WebServerConfig, redCapConfig: RedCapConfig) extends ICORSHandler with IErrorHandler {

  private val redcapProjectConfigRepository = new RedCapProjectConfigFileRepository(redCapConfig.redcapProjectsFilePath)

  private val notificationEndpoint = new NotificationEndpoint(webServerConfig, redCapConfig, redcapProjectConfigRepository)
  private val redCapProjectConfigEndpoint = new RedCapProjectConfigEndpoint(redCapConfig, redcapProjectConfigRepository)

  lazy val toFHIRRoute: Route =
    pathPrefix(webServerConfig.baseUri) {
      corsHandler {
        extractMethod { _ =>
          extractUri { _ =>
            extractRequestEntity { _ =>
              optionalHeaderValueByName("X-Correlation-Id") { _ =>
                handleRejections(RejectionHandler.default) { // Default rejection handling
                  handleExceptions(exceptionHandler()) { // Handle exceptions
                    notificationEndpoint.route() ~ redCapProjectConfigEndpoint.route()
                  }
                }
              }
            }
          }
        }
      }
    }
}

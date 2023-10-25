package io.tofhir.redcap.endpoint

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.tofhir.redcap.config.RedCapConfig
import io.tofhir.redcap.endpoint.NotificationEndpoint.SEGMENT_NOTIFICATION
import io.tofhir.redcap.server.config.WebServerConfig
import io.tofhir.redcap.service.NotificationService
import io.tofhir.redcap.service.project.IRedCapProjectConfigRepository

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Endpoint to handle notifications coming from REDCap upon creation/modification of a new record.
 * */
class NotificationEndpoint(webServerConfig: WebServerConfig, redCapConfig: RedCapConfig, redCapProjectConfigRepository: IRedCapProjectConfigRepository) {

  val service: NotificationService = new NotificationService(redCapConfig, redCapProjectConfigRepository)

  def route(): Route = {
    pathPrefix(SEGMENT_NOTIFICATION) {
      pathEndOrSingleSlash {
        handleNotificationRoute() ~ getNotificationURLRoute
      }
    }
  }

  /**
   * Route to handle notifications.
   *
   * @return
   */
  private def handleNotificationRoute(): Route = {
    post {
      // extracts the form fields
      formFieldMap { fields =>
        complete {
          service.handleNotification(fields) map { _ =>
            StatusCodes.OK
          }
        }
      }
    }
  }

  /**
   * Route to return the URL which will accept the notifications from REDCap.
   * The URL to be registered to REDCap.
   *
   * @return
   */
  private def getNotificationURLRoute: Route = {
    get {
      complete {
        s"${webServerConfig.serverLocation}/${webServerConfig.baseUri}/${NotificationEndpoint.SEGMENT_NOTIFICATION}"
      }
    }
  }
}

object NotificationEndpoint {
  val SEGMENT_NOTIFICATION = "notification"
}




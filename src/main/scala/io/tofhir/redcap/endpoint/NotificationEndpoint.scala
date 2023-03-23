package io.tofhir.redcap.endpoint

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, formFieldMap, pathEndOrSingleSlash, pathPrefix, post}
import akka.http.scaladsl.server.Route
import io.tofhir.redcap.endpoint.NotificationEndpoint.SEGMENT_NOTIFICATION
import io.tofhir.redcap.service.NotificationService

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Endpoint to handle notifications coming from REDCap upon creation/modification of a new record.
 * */
class NotificationEndpoint() {

  val service: NotificationService = new NotificationService()

  def route(): Route = {
    pathPrefix(SEGMENT_NOTIFICATION) {
      pathEndOrSingleSlash {
        handleNotificationRoute()
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
}

object NotificationEndpoint {
  val SEGMENT_NOTIFICATION = "notification"
}




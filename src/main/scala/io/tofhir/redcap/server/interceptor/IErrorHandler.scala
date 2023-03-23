package io.tofhir.redcap.server.interceptor

import akka.http.scaladsl.server.{Directives, ExceptionHandler}
import com.typesafe.scalalogging.LazyLogging
import io.tofhir.redcap.model.{InternalRedCapError, ToFhirRedCapError}

trait IErrorHandler extends LazyLogging {

  /**
   * Exception Handler object
   *
   * @return
   */
  def exceptionHandler(): ExceptionHandler =
    ExceptionHandler {
      case e: Exception => Directives.complete {
        val response = exceptionToResponse(e)
        response.statusCode -> response.toString
      }
    }

  /**
   * Handling of exceptions by converting them to ToFhirRedCapError
   *
   * @return
   */
  private def exceptionToResponse: PartialFunction[Exception, ToFhirRedCapError] = {
    case e: ToFhirRedCapError =>
      logger.error(s"toFHIR-REDCap error encountered.", e)
      e
    case e: Exception =>
      logger.error("Unexpected internal error", e)
      InternalRedCapError(s"Unexpected internal error: ${e.getClass.getName}", e.getMessage, Some(e))
  }
}

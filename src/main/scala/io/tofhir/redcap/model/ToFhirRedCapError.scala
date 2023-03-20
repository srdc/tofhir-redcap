package io.tofhir.redcap.model

import java.io.{PrintWriter, StringWriter}

/**
 * Any exception thrown by ToFHIR-RedCap server
 */
abstract class ToFhirRedCapError extends Exception {
  /**
   * HTTP status code to return when this error occurs
   */
  val statusCode: Int
  /**
   * Type of the error
   */
  val `type`: String = s"https://tofhir.io/errors/${getClass.getSimpleName}"
  /**
   * Title of the error
   */
  val title: String
  /**
   * Details of the error
   */
  val detail: String

  /**
   * Inner exception
   */
  val cause: Option[Throwable] = None

  override def toString: String = {
    s"Status Code: $statusCode\n" +
      s"Type: ${`type`}\n" +
      s"Title: $title\n" +
      s"Detail: $detail\n" +
      s"Stack Trace: ${if (cause.isDefined) getStackTraceAsString(cause.get)}"
  }

  private def getStackTraceAsString(t: Throwable) = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

}

case class InternalRedCapError(title: String, detail: String, override val cause: Option[Throwable] = None) extends ToFhirRedCapError {
  val statusCode = 500
}
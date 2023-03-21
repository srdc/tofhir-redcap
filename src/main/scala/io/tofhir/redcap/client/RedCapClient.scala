package io.tofhir.redcap.client

import java.net.ConnectException

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.StreamTcpException
import io.tofhir.redcap.Execution.actorSystem
import io.tofhir.redcap.config.ToFhirRedCapConfig
import io.tofhir.redcap.model.GatewayTimeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Client to make use of REDCap API.
 * */
class RedCapClient {

  /**
   * Exports the details of a REDCap record.
   *
   * @param token    The API token
   * @param recordId The identifier of record whose details will be fetched
   * @param instrument The name of instrument to which record belongs to
   * @return the serialization of record details
   * @throws GatewayTimeout when it does not connect to REDCap to retrieve record details
   * */
  def exportRecord(token: String, recordId: String, instrument: String): Future[String] = {
    // create form data to export record
    val formData = Multipart.FormData(
      Multipart.FormData.BodyPart.Strict("token", token),
      Multipart.FormData.BodyPart.Strict("content", "record"),
      Multipart.FormData.BodyPart.Strict("format", "json"), // the format of returned content
      Multipart.FormData.BodyPart.Strict("type", "flat"), // output as one record per row
      Multipart.FormData.BodyPart.Strict("records", s"$recordId"), // the list of records to be exported
      Multipart.FormData.BodyPart.Strict("forms",s"${instrument.replaceAll(" ","_")}"), // the list of forms for which records will be pulled
      Multipart.FormData.BodyPart.Strict("rawOrLabel", "label"), // export labels for the options of multiple choice fields
      Multipart.FormData.BodyPart.Strict("rawOrLabelHeaders", "raw"), //export the variable/field names instead of field labels
      Multipart.FormData.BodyPart.Strict("exportCheckboxLabel", "true"), // export labels for checkboxes instead of Checked or Unchecked
      Multipart.FormData.BodyPart.Strict("exportSurveyFields", "false"), // no need to export survey fields
      Multipart.FormData.BodyPart.Strict("exportDataAccessGroups", "false"), // no need to export data access group
      Multipart.FormData.BodyPart.Strict("returnFormat", "json") // the format of error messages
    ).toEntity

    val httpRequest = HttpRequest(
      uri = s"${ToFhirRedCapConfig.redCapUrl}",
      method = HttpMethods.POST,
      entity = formData
    )

    Http()
      .singleRequest(httpRequest)
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[String].map(str => str.substring(1,str.length-1)) // get rid of array brackets to return serialization of record
      }.recover {
      case e: StreamTcpException => e.getCause match {
        case e: ConnectException => throw GatewayTimeout("REDCap unavailable!", "Can not connect to REDCap to retrieve record details.", Some(e))
      }
    }
  }
}

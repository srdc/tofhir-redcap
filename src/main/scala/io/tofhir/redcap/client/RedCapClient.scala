package io.tofhir.redcap.client

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.StreamTcpException
import io.tofhir.redcap.Execution.actorSystem
import io.tofhir.redcap.config.ToFhirRedCapConfig
import io.tofhir.redcap.model.json.Json4sSupport._
import io.tofhir.redcap.model.{BadRequest, GatewayTimeout, Instrument, InternalRedCapError}
import org.json4s.JsonAST.{JArray, JValue}

import java.net.{ConnectException, UnknownHostException}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Client to make use of REDCap API.
 * */
class RedCapClient {

  /**
   * Exports the details of a REDCap record.
   *
   * @param token      The API token
   * @param recordId   The identifier of record whose details will be fetched
   * @param instrument The name of instrument to which record belongs to
   * @param projectId  The identifier of project to which record belongs to
   * @return record details as JValue
   * @throws GatewayTimeout      when it can not connect to REDCap to retrieve record details
   * @throws InternalRedCapError when it can not resolve the IP address of REDCAP API URL
   * */
  def exportRecord(token: String, recordId: String, instrument: String, projectId: String): Future[JValue] = {
    val httpRequest = getREDCapHttpRequest(token, Some(instrument), Some(recordId))

    Http()
      .singleRequest(httpRequest)
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[JArray].map(arr => {
            if (arr.arr.isEmpty)
              throw BadRequest("No record exported!", s"Either the record '$recordId' for instrument '$instrument' in project '$projectId' does not exist or the token does not belong to the project '$projectId'")
            // the record is returned in an array, therefore get the first element
            arr.apply(0)
          })
        case HttpResponse(StatusCodes.Forbidden, _, entity, _) =>
          val msg = entity.asInstanceOf[HttpEntity.Strict].data.utf8String
          throw BadRequest("Permission error!", s"Token for project '$projectId' does not have permission to export a REDCap record. The error message from REDCap Export Record API is $msg")
      }.recover {
        case e: StreamTcpException => e.getCause match {
          case e: ConnectException => throw GatewayTimeout("REDCap unavailable!", "Can not connect to REDCap to retrieve record details.", Some(e))
          case e: UnknownHostException => throw InternalRedCapError("REDCap unavailable!", s"IP address of the given REDCap API URL '${e.getMessage}' could not be determined", Some(e))
        }
      }
  }

  /**
   * Exports the details of REDCap records which belong to the given instrument.
   *
   * @param token      The API token
   * @param instrument The name of instrument to which records belong to
   * @param projectId  The identifier of project to which instrument belongs to
   * @return records as JArray
   * @throws GatewayTimeout      when it can not connect to REDCap
   * @throws InternalRedCapError when it can not resolve the IP address of REDCAP API URL
   * */
  def exportRecords(token: String, instrument: String, projectId: String): Future[JArray] = {
    val httpRequest = getREDCapHttpRequest(token, Some(instrument))

    Http()
      .singleRequest(httpRequest)
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[JArray]
        case HttpResponse(StatusCodes.Forbidden, _, entity, _) =>
          val msg = entity.asInstanceOf[HttpEntity.Strict].data.utf8String
          throw BadRequest("Permission error!", s"Token for project '$projectId' does not have permission to export REDCap records. The error message from REDCap Export Record API is $msg")
      }.recover {
        case e: StreamTcpException => e.getCause match {
          case e: ConnectException => throw GatewayTimeout("REDCap unavailable!", "Can not connect to REDCap to export records.", Some(e))
          case e: UnknownHostException => throw InternalRedCapError("REDCap unavailable!", s"IP address of the given REDCap API URL '${e.getMessage}' could not be determined", Some(e))
        }
      }
  }

  /**
   * Exports the instruments of a REDCap project.
   *
   * @param token The API token
   * @return the list of instruments
   * @throws GatewayTimeout      when it can not connect to REDCap
   * @throws InternalRedCapError when it can not resolve the IP address of REDCAP API URL
   * */
  def exportInstruments(token: String): Future[Seq[Instrument]] = {
    val httpRequest = getREDCapHttpRequest(token)

    Http()
      .singleRequest(httpRequest)
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[Seq[Instrument]]
      }.recover {
        case e: StreamTcpException => e.getCause match {
          case e: ConnectException => throw GatewayTimeout("REDCap unavailable!", "Can not connect to REDCap to export instruments.", Some(e))
          case e: UnknownHostException => throw InternalRedCapError("REDCap unavailable!", s"IP address of the given REDCap API URL '${e.getMessage}' could not be determined", Some(e))
        }
      }
  }

  /**
   * Helper function to create a HttpRequest.Token is the only mandatory parameter.
   * If no optional parameters are provided, project instruments will be retrieved.
   * If instrument is given but record id is skipped, then all records of the instrument will be retrieved.
   * If instrument and record id are provided, that record will be retrieved.
   *
   * Providing only recordId is not valid and the project instruments will be retrieved in that case.
   *
   * @param token      API token
   * @param instrument The instrument name
   * @param recordId   The identifier of record
   * @return the HttpRequest to export instruments, all records or a specific record
   * */
  private def getREDCapHttpRequest(token: String, instrument: Option[String] = None, recordId: Option[String] = None): HttpRequest = {
    val bodyParts =
      if (instrument.nonEmpty) {
        // export records
        var parts = Seq(Multipart.FormData.BodyPart.Strict("token", token),
          Multipart.FormData.BodyPart.Strict("content", "record"),
          Multipart.FormData.BodyPart.Strict("format", "json"), // the format of returned content
          Multipart.FormData.BodyPart.Strict("type", "flat"), // output as one record per row
          Multipart.FormData.BodyPart.Strict("forms", s"${instrument.get.replaceAll(" ", "_")}"), // the list of forms for which records will be pulled
          Multipart.FormData.BodyPart.Strict("rawOrLabel", "raw"), // export labels for the options of multiple choice fields
          Multipart.FormData.BodyPart.Strict("rawOrLabelHeaders", "raw"), //export the variable/field names instead of field labels
          Multipart.FormData.BodyPart.Strict("exportCheckboxLabel", "true"), // export labels for checkboxes instead of Checked or Unchecked
          Multipart.FormData.BodyPart.Strict("exportSurveyFields", "false"), // no need to export survey fields
          Multipart.FormData.BodyPart.Strict("exportDataAccessGroups", "false"), // no need to export data access group
          Multipart.FormData.BodyPart.Strict("returnFormat", "json") // the format of error messages
        )
        // export a specific record
        if (recordId.nonEmpty)
          parts = parts :+ Multipart.FormData.BodyPart.Strict("records", s"${recordId.get}")
        parts
      } else {
        // export instrument
        Seq(Multipart.FormData.BodyPart.Strict("token", token),
          Multipart.FormData.BodyPart.Strict("content", "instrument"),
          Multipart.FormData.BodyPart.Strict("format", "json"), // the format of returned content
        )
      }

    HttpRequest(
      uri = s"${ToFhirRedCapConfig.redCapUrl}",
      method = HttpMethods.POST,
      entity = Multipart.FormData(bodyParts: _*).toEntity
    )
  }
}

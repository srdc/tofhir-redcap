package io.tofhir.redcap.service

import io.tofhir.redcap.config.{RedCapProjectConfig, ToFhirRedCapConfig}
import io.tofhir.redcap.model.BadRequest

import scala.concurrent.Future

/**
 * Service to handle a REDCap Notification which is sent by REDCap whenever a new record is created or an existing one is updated.
 * */
class NotificationService {

  /**
   * Handles the REDCap notification for an updated/created record. It extracts the record id from the given form fields and
   * makes a call to REDCap API to export record details. Then, it publishes the record to corresponding Kafka topic.
   *
   * @param formDataFields Form fields of a REDCap notification
   */
  def handleNotification(formDataFields: Map[String, String]): Future[Unit] = {
    val recordId: String = formDataFields(RedCapNotificationFormFields.RECORD)
    val projectId: String = formDataFields(RedCapNotificationFormFields.PROJECT_ID)
    val instrument: String = formDataFields(RedCapNotificationFormFields.INSTRUMENT)
    val token: String = getRedCapProjectToken(projectId)
    // TODO: export record and publish it to Kafka topic
    Future.successful()
  }

  /**
   * Returns the API token for the given project. API token is extracted from the project configuration.
   *
   * @param projectId REDCap project id
   * @return API token for the project
   * @throws BadRequest if there is no configuration for the given project
   * */
  private def getRedCapProjectToken(projectId: String): String = {
    val projectConfig: Option[RedCapProjectConfig] = ToFhirRedCapConfig.redCapProjectsConfig.find(p => p.id.contentEquals(projectId))
    if (projectConfig.isEmpty)
      throw BadRequest("Invalid Project !", s"Project configuration is missing for project '$projectId'")
    projectConfig.get.token
  }
}

/**
 * Keeps the form fields for a REDCap notification
 * */
object RedCapNotificationFormFields {
  val RECORD = "record" // identifier of record which is updated/created
  val PROJECT_ID = "project_id" // project id to which record belongs
  val INSTRUMENT = "instrument" // name of the form to which record belongs
}
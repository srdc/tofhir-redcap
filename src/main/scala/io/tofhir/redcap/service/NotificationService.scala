package io.tofhir.redcap.service

import com.typesafe.scalalogging.LazyLogging
import io.tofhir.redcap.client.RedCapClient
import io.tofhir.redcap.config.{RedCapProjectConfig, ToFhirRedCapConfig}
import io.tofhir.redcap.model.BadRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Service to handle a REDCap Notification which is sent by REDCap whenever a new record is created or an existing one is updated.
 * */
class NotificationService extends LazyLogging {

  // Kafka service to publish record to a Kafka topic
  val kafkaService: KafkaService = new KafkaService()
  // REDCap API client to export record details
  val redCapClient: RedCapClient = new RedCapClient()

  // handle 'redcap.publishRecordsAtStartup' configuration
  if (ToFhirRedCapConfig.publishRecordsAtStartup) {
    logger.info("'redcap.publishRecordsAtStartup' is enabled. Will export REDCap records and publish them to Kafka...")
    handleStartUpNotification()
  }

  /**
   * Handles the REDCap notification for an updated/created record. It extracts the record id from the given form fields and
   * makes a call to REDCap API to export record details. Then, it publishes the record to corresponding Kafka topic.
   *
   * @param formDataFields Form fields of a REDCap notification
   * @throws BadRequest when one of mandatory keys of the from data is missing
   */
  def handleNotification(formDataFields: Map[String, String]): Future[Unit] = {
    // form data can be empty when the endpoint is tested while setting up Data Entry Trigger functionality of REDCap
    if (formDataFields.isEmpty) {
      Future.successful()
    }
    else {
      if (!formDataFields.contains(RedCapNotificationFormFields.RECORD) || !formDataFields.contains(RedCapNotificationFormFields.PROJECT_ID) || !formDataFields.contains(RedCapNotificationFormFields.INSTRUMENT))
        throw BadRequest("Invalid Form Data !", s"One of the mandatory keys '${RedCapNotificationFormFields.RECORD}', '${RedCapNotificationFormFields.PROJECT_ID}' or '${RedCapNotificationFormFields.INSTRUMENT}' is missing in the form data of the notification request.")
      val recordId: String = formDataFields(RedCapNotificationFormFields.RECORD)
      val projectId: String = formDataFields(RedCapNotificationFormFields.PROJECT_ID)
      val instrument: String = formDataFields(RedCapNotificationFormFields.INSTRUMENT)
      val token: String = getRedCapProjectToken(projectId)

      redCapClient.exportRecord(token, recordId, instrument, projectId).map(record => {
        kafkaService.publishRedCapRecord(getTopicName(projectId, instrument), record, Some(recordId))
      })
    }
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

  /**
   * This is the method to be called at the startup of server if we would like to retrieve all REDCap records and publish
   * them to Kafka.
   */
  private def handleStartUpNotification(): Unit = {
    // traverse the configured projects to publish their data to Kafka
    ToFhirRedCapConfig.redCapProjectsConfig.foreach(projectConfig => {
      // export project instruments
      redCapClient.exportInstruments(projectConfig.token).map(instruments => {
        // for each instrument export records
        instruments.foreach(instrument => {
          redCapClient.exportRecords(projectConfig.token, instrument.instrument_name, projectConfig.id).map(records => {
            // publish them to Kafka
            kafkaService.publishRedCapRecords(getTopicName(projectConfig.id, instrument.instrument_name), records)
          })
        })
      })
    })
  }

  /**
   * Returns the Kafka topic name for the given project and instrument. Project id and instrument name are concatenated
   * to create a unique topic name.
   *
   * @param projectId  the identifier of REDCap project
   * @param instrument the name of instrument
   * @return the corresponding Kafka topic name
   * */
  private def getTopicName(projectId: String, instrument: String): String = {
    s"$projectId-$instrument"
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
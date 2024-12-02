package io.tofhir.redcap.service

import io.tofhir.redcap.Execution.actorSystem.dispatcher
import io.tofhir.redcap.client.RedCapClient
import io.tofhir.redcap.config.RedCapConfig
import io.tofhir.redcap.model.{BadRequest, RedCapProjectConfig}
import io.tofhir.redcap.service.project.IRedCapProjectConfigRepository

import scala.concurrent.Future

/**
 * Service to handle a REDCap Notification which is sent by REDCap whenever a new record is created or an existing one is updated.
 * */
class NotificationService(redCapConfig: RedCapConfig, redCapProjectConfigRepository: IRedCapProjectConfigRepository) {
  // Kafka service to publish record to a Kafka topic
  val kafkaService: KafkaService = new KafkaService()
  // REDCap API client to export record details
  val redCapClient: RedCapClient = new RedCapClient(redCapConfig.redCapUrl)

  /**
   * Handles the REDCap notification for an updated/created record. It extracts the record id from the given form fields and
   * makes a call to REDCap API to export record details. Then, it publishes the record to corresponding Kafka topic.
   *
   * @param formDataFields Form fields of a REDCap notification
   * @throws BadRequest when one of mandatory keys of the form data is missing
   */
  def handleNotification(formDataFields: Map[String, String]): Future[Unit] = {
    // form data can be empty when the endpoint is tested while setting up Data Entry Trigger functionality of REDCap
    if (formDataFields.isEmpty) {
      Future {}
    }
    else {
      if (!formDataFields.contains(RedCapNotificationFormFields.RECORD) || !formDataFields.contains(RedCapNotificationFormFields.PROJECT_ID) || !formDataFields.contains(RedCapNotificationFormFields.INSTRUMENT))
        throw BadRequest("Invalid Form Data !", s"One of the mandatory keys '${RedCapNotificationFormFields.RECORD}', '${RedCapNotificationFormFields.PROJECT_ID}' or '${RedCapNotificationFormFields.INSTRUMENT}' is missing in the form data of the notification request.")
      val recordId: String = formDataFields(RedCapNotificationFormFields.RECORD)
      val projectId: String = formDataFields(RedCapNotificationFormFields.PROJECT_ID)
      val instrument: String = formDataFields(RedCapNotificationFormFields.INSTRUMENT)
      getRedCapProject(projectId) flatMap { redCapProjectConfig: RedCapProjectConfig =>
        redCapClient.exportRecord(redCapProjectConfig, recordId, instrument) map { record =>
          kafkaService.publishRedCapRecord(KafkaTopicManager.getTopicName(projectId, instrument), record, Some(recordId))
        }
      }
    }
  }

  /**
   * Returns the configuration object of the given project.
   *
   * @param projectId REDCap project id
   * @return configuration of the project
   * @throws BadRequest if there is no configuration for the given project
   * */
  private def getRedCapProject(projectId: String): Future[RedCapProjectConfig] = {
    redCapProjectConfigRepository.getProject(projectId) map { projectConfig: Option[RedCapProjectConfig] =>
      if (projectConfig.isEmpty)
        throw BadRequest("Invalid Project !", s"Project configuration is missing for project '$projectId'")
      projectConfig.get
    }
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

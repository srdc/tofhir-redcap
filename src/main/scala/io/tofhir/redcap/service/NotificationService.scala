package io.tofhir.redcap.service

import com.typesafe.scalalogging.LazyLogging
import io.tofhir.redcap.Execution.actorSystem.dispatcher
import io.tofhir.redcap.client.RedCapClient
import io.tofhir.redcap.config.RedCapConfig
import io.tofhir.redcap.model.json.Json4sSupport.formats
import io.tofhir.redcap.model.{BadRequest, Instrument, RedCapProjectConfig}
import io.tofhir.redcap.service.project.IRedCapProjectConfigRepository
import org.json4s.{JArray, JObject}
import org.json4s.JsonAST.JString

import scala.concurrent.Future

/**
 * Service to handle a REDCap Notification which is sent by REDCap whenever a new record is created or an existing one is updated.
 * */
class NotificationService(redCapConfig: RedCapConfig, redCapProjectConfigRepository: IRedCapProjectConfigRepository) extends LazyLogging {
  // KafkaTopicManager to create Kafka topics for the projects' instruments
  private val kafkaTopicManager: KafkaTopicManager = new KafkaTopicManager(redCapConfig, redCapProjectConfigRepository)

  // Kafka service to publish record to a Kafka topic
  val kafkaService: KafkaService = new KafkaService()
  // REDCap API client to export record details
  val redCapClient: RedCapClient = new RedCapClient(redCapConfig.redCapUrl)

  // create Kafka topics for the configured REDCap projects at the initialization
  kafkaTopicManager.createTopicsForInstruments()
    .map(_ => {
      // handle 'redcap.publishRecordsAtStartup' configuration
      if (redCapConfig.publishRecordsAtStartup) {
        logger.info("'redcap.publishRecordsAtStartup' is enabled. Will export REDCap records and publish them to Kafka...")
        handleStartUpNotification()
      }
    })

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

  /**
   * This is the method to be called at the startup of server if we would like to retrieve all REDCap records and publish
   * them to Kafka.
   */
  private def handleStartUpNotification(): Future[Unit] = {
    // traverse the configured projects to publish their data to Kafka
    redCapProjectConfigRepository.getAllProjects flatMap { redCapProjects: Seq[RedCapProjectConfig] =>
      Future.sequence(
        redCapProjects.map { projectConfig =>
          redCapClient.exportInstruments(projectConfig.token) flatMap { instruments: Seq[Instrument] =>
            Future.sequence(instruments.map { instrument =>
              redCapClient.exportRecords(projectConfig.token, instrument.instrument_name, projectConfig.id) map { records =>
                // flatten the records which are in eav format
                val flattenedRecords: JArray = flattenRecords(records, projectConfig.recordIdField)
                // publish them to Kafka
                kafkaService.publishRedCapRecords(KafkaTopicManager.getTopicName(projectConfig.id, instrument.instrument_name), flattenedRecords)
              }
            })
          }
        }
      )
    } map { _ => } // Get rid of all inner objects since we do not need them
  }

  /**
   * Flattens an array of REDCap records by grouping them based on the combination of "record" and
   * "redcap_repeat_instance" fields and creates a JSON array of objects.
   *
   * The method works as follows:
   *  - Groups the input records based on "record" and "redcap_repeat_instance".
   *  - Collects all fields from each group, excluding "redcap_repeat_instance" and "redcap_repeat_instrument".
   *  - Adds the record ID field to the grouped fields if it is not already present.
   *  - Returns only those records that have more than just the record ID field.
   *
   * @param records       The array of JSON records to be processed.
   * @param recordIdField The name of the field that represents the record ID in the output.
   * @return A JSON array containing flattened records.
   */
  private def flattenRecords(records: JArray, recordIdField: String): JArray = {
    // Group the records by the combination of "record" and "redcap_repeat_instance"
    val groupedRecords = records.arr.groupBy { record =>
      val recordId = (record \ "record").extractOpt[String]
      val repeatInstance = (record \ "redcap_repeat_instance").extractOpt[String]
      (recordId, repeatInstance)
    }

    // Create a list of JObject for groups
    val flattenedRecords = groupedRecords.collect {
      case ((Some(recordId), _), items) =>
        val fields = items.flatMap { item =>
          val fieldName = (item \ "field_name").extract[String]
          val value = item \ "value"
          // Exclude "redcap_repeat_instance" and "redcap_repeat_instrument" fields
          if (fieldName != "redcap_repeat_instance" && fieldName != "redcap_repeat_instrument") {
            Some(fieldName -> value)
          } else {
            None
          }
        }

        // Check if the record ID field is already in the fields
        val updatedFields = if (fields.exists(_._1 == recordIdField)) {
          fields
        } else {
          (recordIdField -> JString(recordId)) :: fields
        }

        // Only include the record if it has more than one field (beyond just the record ID)
        if (updatedFields.size > 1) Some(JObject(updatedFields)) else None
    }.flatten.toList // Remove `None` safely using flatten

    JArray(flattenedRecords)
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

package io.tofhir.redcap.service

import com.typesafe.scalalogging.LazyLogging
import io.tofhir.redcap.Execution.actorSystem.dispatcher
import io.tofhir.redcap.client.RedCapClient
import io.tofhir.redcap.config.RedCapConfig
import io.tofhir.redcap.model.json.Json4sSupport.formats
import io.tofhir.redcap.model.{Instrument, RedCapProjectConfig}
import io.tofhir.redcap.service.project.IRedCapProjectConfigRepository
import org.json4s.JsonAST.JString
import org.json4s.{JArray, JObject}

import scala.collection.mutable
import scala.concurrent.Future

/**
 * Class to make sure that all REDCap instruments have a topic in the configured Kafka
 * */
class KafkaTopicManager(redCapConfig: RedCapConfig, redcapProjectConfigRepository: IRedCapProjectConfigRepository) extends LazyLogging {

  val redCapClient: RedCapClient = new RedCapClient(redCapConfig.redCapUrl)
  val kafkaService: KafkaService = new KafkaService

  // cache to keep existing Kafka topics
  private val topicsCache: mutable.Set[String] = mutable.Set(kafkaService.getTopics.toSeq: _*)

  /**
   * Initializes Kafka topics for the configured REDCap projects or the provided one.
   *
   * This method performs the following key actions:
   * 1. If the `publishRecordsAtStartup` configuration is enabled,
   * it deletes all existing Kafka topics to prevent duplication of records.
   * 2. Creates Kafka topics based on the configured REDCap projects.
   * 3. If `publishRecordsAtStartup` is enabled, it exports all REDCap records
   * and publishes them to the corresponding Kafka topics for processing.
   *
   * @param projectId The identifier of the project for which the Kafka topics should be populated.
   * @return A `Future[Unit]` that completes when all Kafka topics are initialized,
   *         and records are published if required.
   */
  def initializeTopics(deleteTopics: Boolean, publishRecords: Boolean, projectId: Option[String] = None): Future[Unit] = {
    // Delete existing topics to prevent duplication of records
    if (deleteTopics) {
      kafkaService.deleteTopics(projectId)
      topicsCache.clear() // Clear cached topic information
    }
    // retrieve project configurations
    val projectConfigs: Future[Seq[RedCapProjectConfig]] = projectId match {
      case Some(id) =>
        redcapProjectConfigRepository.getProject(id).map {
          case Some(project) => Seq(project) // Wrap the single project in a sequence
          case None => Seq.empty // Return an empty sequence if the project is not found
        }
      case None =>
        redcapProjectConfigRepository.getAllProjects // Return all projects if no specific ID is provided
    }
    // Wait for both topic creation and record publishing to complete before returning success
    projectConfigs.flatMap { configs =>
      // Create Kafka topics
      createTopicsForInstruments(configs).flatMap { _ =>
        if (publishRecords) {
          // Publish REDCap records if required
          publishRedCapRecordsForProjects(configs)
        } else {
          // No need to publish records, just return a successful Future
          Future.successful(())
        }
      }
    }
  }

  /**
   * Creates Kafka topics for instruments of the provided RedCap projects and updates the topics cache.
   *
   * @param redCapProjects A sequence of REDCap project configurations, which include information about each project
   *                       (e.g., project token, instruments, and record ID fields).
   * @return A Future[Unit] indicating the completion of the operation.
   */
  def createTopicsForInstruments(redCapProjects: Seq[RedCapProjectConfig]): Future[Unit] = {
    // retrieve topic names for the all project instruments
    val allTopicsFuture: Future[Seq[Seq[String]]] = Future.sequence(
      redCapProjects.map { projectConfig =>
        redCapClient.exportInstruments(projectConfig.token).map { instruments: Seq[Instrument] =>
          instruments.map(instrument => KafkaTopicManager.getTopicName(projectConfig.id, instrument.instrument_name))
        }
      }
    )

    // Use flatMap to call createTopics once with all topics
    allTopicsFuture.flatMap { allTopics: Seq[Seq[String]] =>
      // Flatten the Seq[Seq[String]] to Seq[String]
      // and filter out the existing topics
      val flattenedTopics = allTopics.flatten
        .filter(topic => !topicsCache.contains(topic))
      // create the topics if any
      if (flattenedTopics.nonEmpty) {
        val createdTopics = kafkaService.createTopics(flattenedTopics)
        // update the cache
        createdTopics.foreach(topic => topicsCache.add(topic))
      }
      Future.successful(())
    }
  }

  /**
   * Publishes all REDCap records to Kafka during the server startup for the provided REDCap projects.
   *
   * @param redCapProjects A sequence of REDCap project configurations, which include information about each project
   *                       (e.g., project token, instruments, and record ID fields).
   * @return A `Future[Unit]` that completes once all REDCap records have been exported and published to Kafka for
   *         the given projects.
   */
  private def publishRedCapRecordsForProjects(redCapProjects: Seq[RedCapProjectConfig]): Future[Unit] = {
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
    ) map { _ => } // Get rid of all inner objects since we do not need them
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

object KafkaTopicManager {
  /**
   * Returns the Kafka topic name for the given project and instrument. Project id and instrument name are concatenated
   * to create a unique topic name.
   *
   * @param projectId  the identifier of REDCap project
   * @param instrument the name of instrument
   * @return the corresponding Kafka topic name
   * */
  def getTopicName(projectId: String, instrument: String): String = {
    s"${getTopicPrefix(projectId)}$instrument"
  }

  /**
   * Generates a Kafka topic prefix based on the provided project ID.
   *
   * @param projectId The ID of the project for which the topic prefix is to be generated.
   * @return A string representing the Kafka topic prefix for the specified project.
   *         For example, if the project ID is "project1", the returned prefix will be "project1-".
   */
  def getTopicPrefix(projectId: String): String = {
    s"$projectId-"
  }
}
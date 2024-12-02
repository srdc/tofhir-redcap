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
   * Initializes Kafka topics for the configured REDCap projects during startup.
   *
   * This method performs the following key actions:
   * 1. If the `publishRecordsAtStartup` configuration is enabled,
   * it deletes all existing Kafka topics to prevent duplication of records.
   * 2. Creates Kafka topics based on the configured REDCap projects.
   * 3. If `publishRecordsAtStartup` is enabled, it exports all REDCap records
   * and publishes them to the corresponding Kafka topics for processing.
   *
   * @return A `Future[Unit]` that completes when all Kafka topics are initialized,
   *         and records are published if required.
   */
  def initializeTopics(): Future[Unit] = {
    // Delete existing topics to prevent duplication of records
    if (redCapConfig.publishRecordsAtStartup) {
      logger.info("'redcap.publishRecordsAtStartup' is enabled. Will delete existing Kafka topics...")
      kafkaService.deleteAllTopics()
      topicsCache.clear() // Clear cached topic information
    }
    // Create Kafka topics and optionally publish REDCap records
    createTopicsForInstruments().flatMap { _ =>
      if (redCapConfig.publishRecordsAtStartup) {
        logger.info("'redcap.publishRecordsAtStartup' is enabled. Will export REDCap records and publish them to Kafka...")
        publishRedCapRecordsForAllProjects()
      } else {
        Future.successful(())
      }
    }
  }

  /**
   * Creates Kafka topics for instruments in multiple RedCap projects and updates the topics cache.
   *
   * @return A Future[Unit] indicating the completion of the operation.
   */
  def createTopicsForInstruments(): Future[Unit] = {
    // retrieve projects
    redcapProjectConfigRepository.getAllProjects.flatMap { redCapProjects: Seq[RedCapProjectConfig] =>
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
  }

  /**
   * Publishes all REDCap records to Kafka during the server startup.
   */
  private def publishRedCapRecordsForAllProjects(): Future[Unit] = {
    // traverse the configured projects to publish their data to Kafka
    redcapProjectConfigRepository.getAllProjects flatMap { redCapProjects: Seq[RedCapProjectConfig] =>
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
    s"$projectId-$instrument"
  }
}
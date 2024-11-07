package io.tofhir.redcap.service

import io.tofhir.redcap.client.RedCapClient
import io.tofhir.redcap.config.RedCapConfig
import io.tofhir.redcap.model.{Instrument, RedCapProjectConfig}
import io.tofhir.redcap.service.project.IRedCapProjectConfigRepository

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Class to make sure that all REDCap instruments have a topic in the configured Kafka
 * */
class KafkaTopicManager(redCapConfig: RedCapConfig, redcapProjectConfigRepository: IRedCapProjectConfigRepository) {

  val redCapClient: RedCapClient = new RedCapClient(redCapConfig.redCapUrl)
  val kafkaService: KafkaService = new KafkaService

  // cache to keep existing Kafka topics
  private val topicsCache: mutable.Set[String] = mutable.Set(kafkaService.getTopics.toSeq: _*)

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
package io.tofhir.redcap

import com.typesafe.scalalogging.LazyLogging
import io.tofhir.redcap.Execution.actorSystem.dispatcher
import io.tofhir.redcap.config.ToFhirRedCapConfig
import io.tofhir.redcap.server.ToFhirRedCapServer
import io.tofhir.redcap.service.KafkaTopicManager
import io.tofhir.redcap.service.project.RedCapProjectConfigFileRepository

/**
 * Entrypoint of the application
 */
object Boot extends App with LazyLogging {
  // Initialize the REDCap project configuration repository
  val redcapProjectConfigRepository = new RedCapProjectConfigFileRepository(ToFhirRedCapConfig.redCapConfig.redcapProjectsFilePath)
  // Initialize the KafkaTopicManager
  val kafkaTopicManager: KafkaTopicManager = new KafkaTopicManager(ToFhirRedCapConfig.redCapConfig, redcapProjectConfigRepository)

  // Retrieve the flag that indicates whether REDCap records should be published to Kafka at startup
  private val publishRecordsAtStartup = ToFhirRedCapConfig.redCapConfig.publishRecordsAtStartup
  // If the flag is enabled, log the message indicating that Kafka topics will be deleted and records will be published
  if (publishRecordsAtStartup) {
    logger.info("'redcap.publishRecordsAtStartup' is enabled. Will delete existing Kafka topics and publish REDCap records to Kafka...")
  }

  // Create Kafka topics for each configured REDCap project, passing the flag to control the topic initialization and record publishing
  kafkaTopicManager.initializeTopics(publishRecordsAtStartup, publishRecordsAtStartup)
    .map { _ =>
      // Once Kafka topics are successfully initialized, start the ToFHIR REDCap server
      ToFhirRedCapServer.start()
    }
}
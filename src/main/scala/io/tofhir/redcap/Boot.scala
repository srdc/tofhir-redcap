package io.tofhir.redcap

import io.tofhir.redcap.Execution.actorSystem.dispatcher
import io.tofhir.redcap.config.ToFhirRedCapConfig
import io.tofhir.redcap.server.ToFhirRedCapServer
import io.tofhir.redcap.service.KafkaTopicManager
import io.tofhir.redcap.service.project.RedCapProjectConfigFileRepository

/**
 * Entrypoint of the application
 */
object Boot extends App {
  // Initialize the REDCap project configuration repository
  val redcapProjectConfigRepository = new RedCapProjectConfigFileRepository(ToFhirRedCapConfig.redCapConfig.redcapProjectsFilePath)
  // Initialize the KafkaTopicManager
  val kafkaTopicManager: KafkaTopicManager = new KafkaTopicManager(ToFhirRedCapConfig.redCapConfig, redcapProjectConfigRepository)
  // Create Kafka topics for each configured REDCap project and wait for the completion of the initialization
  kafkaTopicManager.initializeTopics()
    .map(_ =>
      // Start the ToFHIR REDCap server once the Kafka topics are successfully initialized
      ToFhirRedCapServer.start()
    )
}
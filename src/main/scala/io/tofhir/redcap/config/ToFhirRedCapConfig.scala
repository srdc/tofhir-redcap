package io.tofhir.redcap.config

import com.typesafe.config.Config
import io.tofhir.redcap.server.config.WebServerConfig

/**
 * Keeps the configurations of toFHIR-REDCap project
 * */
object ToFhirRedCapConfig {

  import io.tofhir.redcap.Execution.actorSystem

  protected lazy val config: Config = actorSystem.settings.config

  /**
   * Web server configurations
   */
  val webServerConfig = new WebServerConfig(config.getConfig("webserver"))
  /**
   * REDCap configurations
   * */
  lazy val redCapConfig: RedCapConfig = new RedCapConfig(config.getConfig("redcap"))

  /**
   * Kafka configurations
   */
  lazy val kafkaConfig: Config = config.getConfig("kafka")
  /* Kafka servers */
  lazy val kafkaBootstrapServers: String = kafkaConfig.getString("bootstrap-servers")
}

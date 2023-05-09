package io.tofhir.redcap.config

import com.typesafe.config.Config

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Keeps the configurations of toFHIR-REDCap project
 * */
object ToFhirRedCapConfig {

  import io.tofhir.redcap.Execution.actorSystem

  protected lazy val config: Config = actorSystem.settings.config

  /**
   * REDCap configurations
   * */
  lazy val redCapConfig: Config = config.getConfig("redcap")
  /* REDCap API Url */
  lazy val redCapUrl: String = redCapConfig.getString("url")
  /* Whether REDCap records should be published to Kafka at the startup of server */
  lazy val publishRecordsAtStartup: Boolean = redCapConfig.getBoolean("publishRecordsAtStartup")
  /* Configuration of REDCap projects*/
  lazy val redCapProjectsConfig: List[RedCapProjectConfig] = redCapConfig.getConfigList("projects")
    .asScala
    .map(config => new RedCapProjectConfig(config))
    .toList

  /**
   * Kafka configurations
   */
  lazy val kafkaConfig: Config = config.getConfig("kafka")
  /* Kafka servers */
  lazy val kafkaBootstrapServers: String = kafkaConfig.getString("bootstrap-servers")
}

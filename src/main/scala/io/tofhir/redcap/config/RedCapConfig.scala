package io.tofhir.redcap.config

import com.typesafe.config.Config

class RedCapConfig(redCapConfig: Config) {
  /* REDCap API Url */
  lazy val redCapUrl: String = redCapConfig.getString("url")
  /* Whether REDCap records should be published to Kafka at the startup of server */
  lazy val publishRecordsAtStartup: Boolean = redCapConfig.getBoolean("publishRecordsAtStartup")
  /* Path to the JSON file keeping a JSON array of #RedCapProjectConfigs */
  lazy val redcapProjectsFilePath: String = redCapConfig.getString("projects.filePath")

  //  /* Configuration of REDCap projects*/
  //  lazy val redCapProjectsConfig: List[RedCapProjectConfig] = redCapConfig.getConfigList("projects")
  //    .asScala
  //    .map(config => new RedCapProjectConfig(config))
  //    .toList
}

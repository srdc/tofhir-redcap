package io.tofhir.redcap.config

import com.typesafe.config.Config

/**
 * Configuration of a REDCap project
 * */
class RedCapProjectConfig(config: Config) {
  /**
   * Identifier of REDCap project
   * */
  lazy val id: String = config.getString("id")
  /**
   * API token of REDCap project
   * */
  lazy val token: String = config.getString("token")
}

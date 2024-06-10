package io.tofhir.redcap.endpoint

import akka.http.scaladsl.server.Directives.{complete, get, pathEndOrSingleSlash, pathPrefix}
import akka.http.scaladsl.server.Route
import io.tofhir.redcap.endpoint.MetadataEndpoint.SEGMENT_METADATA

import java.util.Properties

/**
 * Endpoint to return metadata of the server.
 */
class MetadataEndpoint {

  def route(): Route = {
    pathPrefix(SEGMENT_METADATA) {
      pathEndOrSingleSlash {
        getMetadata
      }
    }
  }

  /**
   * Returns the metadata of the server. Only version info included for now.
   * */
  private def getMetadata: Route = {
    get {
      complete {
        val properties: Properties  = new Properties()
        properties.load(getClass.getClassLoader.getResourceAsStream("version.properties"))
        s"${properties.getProperty("application.version")}"
      }
    }
  }

}

object MetadataEndpoint {
  val SEGMENT_METADATA = "metadata"
}

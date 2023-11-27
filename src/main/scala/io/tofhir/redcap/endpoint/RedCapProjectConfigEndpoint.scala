package io.tofhir.redcap.endpoint

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.tofhir.redcap.Execution.actorSystem.dispatcher
import io.tofhir.redcap.config.RedCapConfig
import io.tofhir.redcap.endpoint.RedCapProjectConfigEndpoint.SEGMENT_PROJECT_CONFIG
import io.tofhir.redcap.model.RedCapProjectConfig
import io.tofhir.redcap.model.json.Json4sSupport._
import io.tofhir.redcap.service.KafkaTopicManager
import io.tofhir.redcap.service.project.IRedCapProjectConfigRepository

import scala.concurrent.Future
import scala.util.Success

class RedCapProjectConfigEndpoint(redCapConfig: RedCapConfig, redcapProjectConfigRepository: IRedCapProjectConfigRepository) {

  private val kafkaTopicManager: KafkaTopicManager = new KafkaTopicManager(redCapConfig, redcapProjectConfigRepository: IRedCapProjectConfigRepository)

  def route(): Route = {
    pathPrefix(SEGMENT_PROJECT_CONFIG) {
      pathEndOrSingleSlash {
        saveProjectsRoute() ~ getProjectsRoute
      }
    }
  }

  private def saveProjectsRoute(): Route = {
    post {
      entity(as[Seq[RedCapProjectConfig]]) { projects =>
        // Save projects
        val saveProjectsFuture: Future[Seq[RedCapProjectConfig]] =
          redcapProjectConfigRepository.saveProjects(projects)

        // Create Kafka topics for REDCap instruments in the background when saveProjectsFuture completes
        saveProjectsFuture.onComplete {
          case Success(_) =>
            kafkaTopicManager.createTopicsForInstruments()
        }

        // Complete the request with the savedProjects
        onComplete(saveProjectsFuture) {
          case Success(savedProjects) => complete(StatusCodes.Created -> savedProjects)
        }
      }
    }
  }

  /**
   * Route to get all projects
   *
   * @return
   */
  private def getProjectsRoute: Route = {
    get {
      complete {
        redcapProjectConfigRepository.getAllProjects
      }
    }
  }
}

object RedCapProjectConfigEndpoint {
  val SEGMENT_PROJECT_CONFIG = "projects"
}

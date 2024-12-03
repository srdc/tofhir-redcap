package io.tofhir.redcap.endpoint

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.tofhir.redcap.Execution.actorSystem.dispatcher
import io.tofhir.redcap.config.RedCapConfig
import io.tofhir.redcap.endpoint.RedCapProjectConfigEndpoint.{PARAMETER_RELOAD, SEGMENT_PROJECT_CONFIG, SEGMENT_PROJECT_DATA}
import io.tofhir.redcap.model.RedCapProjectConfig
import io.tofhir.redcap.model.json.Json4sSupport._
import io.tofhir.redcap.service.KafkaTopicManager
import io.tofhir.redcap.service.project.IRedCapProjectConfigRepository

import scala.concurrent.Future
import scala.util.Success

class RedCapProjectConfigEndpoint(redCapConfig: RedCapConfig, redcapProjectConfigRepository: IRedCapProjectConfigRepository) {

  private val kafkaTopicManager: KafkaTopicManager = new KafkaTopicManager(redCapConfig, redcapProjectConfigRepository)

  def route(): Route = {
    pathPrefix(SEGMENT_PROJECT_CONFIG) {
      pathEndOrSingleSlash {
        saveProjectsRoute() ~ getProjectsRoute
      } ~
        pathPrefix(SEGMENT_PROJECT_DATA) {
          pathEndOrSingleSlash {
            deleteProjectDataRoute(None) // Handle the case without a projectId
          } ~
            path(Segment) { projectId =>
              deleteProjectDataRoute(Some(projectId)) // Handle the case with a projectId
            }
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
          case Success(projectConfigs) =>
            kafkaTopicManager.createTopicsForInstruments(projectConfigs)
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

  /**
   * Defines a DELETE route to handle the deletion of project data.
   *
   * @param projectId The optional project ID. If provided, only data related to the specified project will be deleted.
   *                  If not provided, all data will be deleted.
   */
  private def deleteProjectDataRoute(projectId: Option[String]): Route = {
    delete {
      parameters(PARAMETER_RELOAD.optional) { reloadParam =>
        complete {
          val reload = reloadParam.contains("true")
          // Ensure we await the result of the initialization before responding
          kafkaTopicManager.initializeTopics(deleteTopics = true, publishRecords = reload, projectId = projectId).map { _ =>
            // Return success once the operation is completed
            StatusCodes.OK
          }
        }
      }
    }
  }
}

object RedCapProjectConfigEndpoint {
  val SEGMENT_PROJECT_CONFIG = "projects"
  val SEGMENT_PROJECT_DATA = "data"
  val PARAMETER_RELOAD = "reload"
}

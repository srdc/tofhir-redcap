package io.tofhir.redcap.endpoint

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.tofhir.redcap.endpoint.RedCapProjectConfigEndpoint.SEGMENT_PROJECT_CONFIG
import io.tofhir.redcap.service.project.IRedCapProjectConfigRepository
import io.tofhir.redcap.Execution.actorSystem.dispatcher
import io.tofhir.redcap.model.RedCapProjectConfig
import io.tofhir.redcap.model.json.Json4sSupport._

class RedCapProjectConfigEndpoint(redcapProjectConfigRepository: IRedCapProjectConfigRepository) {

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
        complete {
          redcapProjectConfigRepository.saveProjects(projects) map { savedProjects =>
            StatusCodes.Created -> savedProjects
          }
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
  val SEGMENT_PROJECT_CONFIG= "projects"
}

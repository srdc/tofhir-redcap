package io.tofhir.redcap.service.project

import io.tofhir.redcap.model.RedCapProjectConfig
import scala.concurrent.Future

// TODO: This is very similar to IProjectRepository (the classes under io.tofhir.server.service.project). Handle while refactoring!
/**
 * Interface to manage Projects
 */
trait IRedCapProjectConfigRepository {
  /**
   * Retrieve all Projects
   *
   * @return all projects in the repository
   */
  def getAllProjects: Future[Seq[RedCapProjectConfig]]

  /**
   * Save projects to the repository.
   *
   * @param projects projects to be saved
   * @return saved projects
   */
  def saveProjects(projects: Seq[RedCapProjectConfig]): Future[Seq[RedCapProjectConfig]]

  /**
   * Retrieve the project identified by its id.
   *
   * @param id id of the project
   * @return the project
   */
  def getProject(id: String): Future[Option[RedCapProjectConfig]]

}

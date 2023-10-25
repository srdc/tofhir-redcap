package io.tofhir.redcap.service.project

import com.typesafe.scalalogging.LazyLogging
import io.tofhir.redcap.model.json.Json4sSupport._
import io.tofhir.redcap.Execution.actorSystem.dispatcher
import io.tofhir.redcap.model.RedCapProjectConfig
import org.json4s._
import org.json4s.jackson.JsonMethods._

import java.io.{File, FileWriter}
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.concurrent.Future
import scala.io.Source

class RedCapProjectConfigFileRepository(redCapProjectsFilePath: String) extends IRedCapProjectConfigRepository with LazyLogging {

  // Project cache keeping the up-to-date list of projects
  private val projectConfigs: mutable.Map[String, RedCapProjectConfig] = mutable.Map.empty

  init()

  /**
   * Initialize the map cache with the file repository
   */
  private def init(): Unit = {
    val file = new File(redCapProjectsFilePath)
    if (file.exists()) {
      val source = Source.fromFile(file, StandardCharsets.UTF_8.name())
      val fileContent = try source.mkString finally source.close()
      var extractedContent: Seq[RedCapProjectConfig] = Seq.empty
      try {
        extractedContent = parse(fileContent).extract[Seq[RedCapProjectConfig]]
      } catch {
        case e: Exception => logger.error(s"Existing file holding the REDCap project configs cannot be read from $redCapProjectsFilePath", e)
      }

      extractedContent.map(pr => projectConfigs.put(pr.id, pr))
    }
  }

  /**
   * Retrieve all Projects
   *
   * @return all projects in the repository
   */
  override def getAllProjects: Future[Seq[RedCapProjectConfig]] = {
    Future {
      projectConfigs.values.toSeq
    }
  }

  /**
   * Save projects to the repository.
   *
   * @param projects projects to be saved
   * @return saved projects
   */
  override def saveProjects(updatedProjects: Seq[RedCapProjectConfig]): Future[Seq[RedCapProjectConfig]] = {
    Future {
      val file = new File(redCapProjectsFilePath)
      val fw = new FileWriter(file)
      try {
        fw.write(serialization.writePretty(updatedProjects))
        updatedProjects.map(pr => projectConfigs.put(pr.id, pr))
        updatedProjects
      }
      catch {
        case e: Exception =>
          logger.error(s"Cannot write the new set of REDCap project configs to $redCapProjectsFilePath", e)
          projectConfigs.values.toSeq
      }
      finally {
        fw.close()
      }
    }
  }

  /**
   * Retrieve the project identified by its id.
   *
   * @param id id of the project
   * @return the project
   */
  override def getProject(id: String): Future[Option[RedCapProjectConfig]] = {
    Future {
      projectConfigs.get(id)
    }
  }
}

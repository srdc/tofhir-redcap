package io.tofhir.redcap.service

import com.typesafe.scalalogging.LazyLogging
import io.tofhir.redcap.config.ToFhirRedCapConfig
import org.apache.kafka.clients.admin._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import org.json4s.JsonAST.{JArray, JValue}
import org.json4s.jackson.JsonMethods._
import scala.jdk.CollectionConverters._
import java.util
import java.util.Properties
import scala.jdk.CollectionConverters.{MapHasAsScala, SeqHasAsJava, SetHasAsScala}

/**
 * Service to manage Kafka-related operations.
 * */
class KafkaService extends LazyLogging {

  // Properties to set up a Kafka Producer
  val kafkaProducerProps: Properties = {
    val props = new Properties()
    props.put("bootstrap.servers", ToFhirRedCapConfig.kafkaBootstrapServers)
    props.put("key.serializer", classOf[StringSerializer].getName)
    props.put("value.serializer", classOf[StringSerializer].getName)
    props
  }

  /**
   * Publishes the REDCap records to a Kafka topic
   *
   * @param topic   Kafka topic
   * @param records Records to be published as JArray
   * */
  def publishRedCapRecords(topic: String, records: JArray): Unit = {
    logger.info(s"Publishing ${records.arr.size} record(s) to '$topic'")
    // publish each record
    records.arr.foreach(record => {
      publishRedCapRecord(topic, record)
    })
  }

  /**
   * Publishes the REDCap record to a Kafka topic
   *
   * @param topic    Kafka topic
   * @param record   REDCap record as JValue
   * @param recordId The identifier of REDCap record.
   * */
  def publishRedCapRecord(topic: String, record: JValue, recordId: Option[String] = None): Unit = {
    // log recordId if provided
    if (recordId.nonEmpty)
      logger.info(s"Publishing record '${recordId.get}' to topic '$topic'")
    var producer: KafkaProducer[String, String] = null
    try {
      producer = new KafkaProducer(kafkaProducerProps)
      // use compact(render(X)) to convert JValue into a string
      producer.send(new ProducerRecord[String, String](topic, compact(render(record))))
    }
    finally {
      if (producer != null)
        producer.close()
    }
  }

  /**
   * Creates Kafka topics with the specified names and returns the names of the successfully created topics.
   *
   * @param topicNames A sequence of topic names to be created.
   * @return A sequence of topic names that were successfully created.
   */
  def createTopics(topicNames: Seq[String]): Seq[String] = {
    val adminClient: AdminClient = createAdminClient()
    try {
      // Convert Seq[String] to java.util.Collection[NewTopic]
      val newTopics: util.Collection[NewTopic] = topicNames.map(topic => new NewTopic(topic, 1, 1.toShort))
        .asJava

      // Create topics using the admin client
      val createTopicsResult = adminClient.createTopics(newTopics)

      // Wait for all topics to be created
      val topicsMap = createTopicsResult.values().asScala.toMap

      // Iterate over the topicsMap to handle each topic individually
      // and find the successfully created ones
      var createdTopics = Seq.empty[String]
      topicsMap.foreach { case (topic, kafkaFuture) =>
        try {
          kafkaFuture.get() // wait for the topic creation to complete
          createdTopics = createdTopics :+ topic
          logger.info(s"Topic $topic created successfully")
        } catch {
          case ex: Exception => logger.error(s"Failed to create topic $topic: ${ex.getMessage}")
        }
      }
      createdTopics
    } finally {
      try {
        // Close the admin client
        adminClient.close()
      } catch {
        case ex: Exception => logger.error(s"Failed to close admin client: ${ex.getMessage}")
      }
    }
  }

  /**
   * Deletes Kafka topics in the configured Kafka cluster.
   *
   * Depending on the `projectId` provided, this method either deletes all topics or only the topics related to the specified project.
   * It retrieves the list of available Kafka topics and deletes them using the Kafka `AdminClient`.
   * Ensures that the `AdminClient` is properly closed after the operation to release resources.
   *
   * @param projectId Optional project ID to filter topics for deletion. If provided, only topics related to the project are deleted.
   *                  If not provided, all topics in the Kafka cluster will be deleted.
   */
  def deleteTopics(projectId: Option[String] = None): Unit = {
    // Fetch all available topics from Kafka
    val allTopics: Set[String] = getTopics

    // Determine which topics to delete based on the provided project ID
    val topicsToBeDeleted: Set[String] = projectId match {
      // Filter topics by project ID if provided
      case Some(id) => allTopics.filter(topic => topic.startsWith(KafkaTopicManager.getTopicPrefix(id)))
      // Delete all topics if no project ID is specified
      case None => allTopics
    }

    // Proceed only if there are topics to delete
    if (topicsToBeDeleted.nonEmpty) {
      // Create an AdminClient for managing Kafka topics
      val adminClient: AdminClient = createAdminClient()
      try {
        // Attempt to delete the selected topics
        adminClient.deleteTopics(topicsToBeDeleted.asJava).all().get()
      } catch {
        case e: Exception =>
          logger.error("Failed to delete Kafka topics")
          throw e
      } finally {
        // Ensure the AdminClient is closed
        adminClient.close()
      }
    }
  }

  /**
   * Creates and returns an instance of Kafka AdminClient used for administrative operations
   * such as listing topics.
   *
   * @return An instance of Kafka AdminClient.
   */
  private def createAdminClient(): AdminClient = {
    // Kafka admin client properties
    val adminProperties = new Properties()
    adminProperties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, ToFhirRedCapConfig.kafkaBootstrapServers)
    // Create an admin client
    AdminClient.create(adminProperties)
  }

  /**
   * Retrieves the set of topics available in the Kafka cluster.
   *
   * @return A Set of topic names present in the Kafka cluster.
   */
  def getTopics: Set[String] = {
    val adminClient: AdminClient = createAdminClient()
    val listTopicsOptions = new ListTopicsOptions().listInternal(false)
    try {
      val listTopicsResult: ListTopicsResult = adminClient.listTopics(listTopicsOptions)
      listTopicsResult.names().get().asScala.toSet
    } catch {
      case e: Throwable =>
        logger.error("Failed to retrieve topics")
        throw e
    } finally {
      try {
        // Close the admin client
        adminClient.close()
      } catch {
        case ex: Exception => logger.error(s"Failed to close admin client: ${ex.getMessage}")
      }
    }
  }
}

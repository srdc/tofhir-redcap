package io.tofhir.redcap.service

import java.util.Properties

import com.typesafe.scalalogging.LazyLogging
import io.tofhir.redcap.config.ToFhirRedCapConfig
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods._

/**
 * Service to publish a REDCap record to a Kafka topic.
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
   * Publishes the REDCap record to a Kafka topic
   *
   * @param topic    Kafka topic
   * @param record   REDCap record as JValue
   * @param recordId The identifier of REDCap record
   * */
  def publishRedCapRecord(topic: String, record: JValue, recordId: String): Unit = {
    logger.info(s"Publishing record '$recordId' to topic '$topic'")
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
}

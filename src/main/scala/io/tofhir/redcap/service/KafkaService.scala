package io.tofhir.redcap.service

import java.util.Properties

import com.typesafe.scalalogging.LazyLogging
import io.tofhir.redcap.config.ToFhirRedCapConfig
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

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
   * @param record   Serialization of REDCap record
   * @param recordId The identifier of REDCap record
   * */
  def publishRedCapRecord(topic: String, record: String, recordId: String): Unit = {
    logger.info(s"Publishing record '$recordId' to topic '$topic'")
    var producer: KafkaProducer[String, String] = null
    try {
      producer = new KafkaProducer(kafkaProducerProps)
      producer.send(new ProducerRecord[String, String](topic, record))
    }
    finally {
      if (producer != null)
        producer.close()
    }
  }
}

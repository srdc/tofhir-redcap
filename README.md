# tofhir-redcap
tofhir-redcap is the toFHIR REDCap integration module. It provides an endpoint which can be set in Data Entry Trigger of a 
REDCap application. When the endpoint receives the notification for a record, it exports its details via REDCap API and publishes it
to Kafka which can be utilized to index REDCap record to an onFhir server.

# Requirements
The project requires the followings to run:
- Java 11
- Scala 2.13
- Maven

# How to build and run 
In the root directory, run the following command to generate an executable jar which includes all dependencies:
```
mvn clean install
```

Run the jar
```
java -jar target/tofhir-redcap-1.0-SNAPSHOT.jar
```

Alternatively, you can run [Boot.scala](src/main/scala/io/tofhir/redcap/Boot.scala) in your chosen IDE, e.g. IntelliJ

Third option is to run it in Docker. After you build the project, go to [docker](docker) directory and run docker-compose.yml:
```
docker-compose up
```

The project starts a server providing an endpoint to handle REDCap notifications. Then, Data Entry Trigger of a 
REDCap can be configured to send notification to http://localhost:8095/tofhir-redcap/notification endpoint (if you use default configurations).

Whenever this endpoint receives a notification about a record, it exports its data via REDCap API and publishes record data
to a Kafka topic which is the concatenation of project id and instrument name such as '27-patient'.

# Configuration
Please see [application.conf](src/main/resources/application.conf) for the configurations. It allows you to configure Web server settings, Kafka
and REDCap projects.
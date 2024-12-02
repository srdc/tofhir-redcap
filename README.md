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

Here is the list of available configurations:

<table>
    <tr>
        <td> Config </td> 
        <td> Description </td>
        <td> Default Value </td>
    </tr>
    <tr>
        <td> webserver.host </td> 
        <td> Hostname that toFHIR-REDCap server will work. Using 0.0.0.0 will bind the server to both localhost and the IP of the server that you deploy it. </td>
        <td> 0.0.0.0 </td>
    </tr>
    <tr>
        <td> webserver.port </td> 
        <td> Port to listen </td>
        <td> 8095 </td>
    </tr>
    <tr>
        <td> webserver.base-uri </td> 
        <td> Base Uri for server e.g. With this default configuration, the root path of toFHIR REDCap server will be http://localhost:8095/tofhir-redcap </td>
        <td> tofhir-redcap </td>
    </tr>
    <tr>
        <td> webserver.ssl.keystore </td> 
        <td> Path to the java keystore for enabling ssl for toFHIR server, use null to disable ssl </td>
        <td> null </td>
    </tr>
    <tr>
        <td> webserver.ssl.password </td> 
        <td> Password of the keystore for enabling ssl for toFHIR server </td>
        <td> null </td>
    </tr>
    <tr>
        <td> kafka.bootstrap-servers </td> 
        <td> Kafka servers separated by comma </td>
        <td> localhost:9092 </td>
    </tr>
    <tr>
        <td> redcap.url </td> 
        <td> REDCap API url </td>
        <td> http://localhost:3000 </td>
    </tr>
    <tr>
        <td> redcap.publishRecordsAtStartup </td> 
        <td> Flag to export REDCap records and publish them to Kafka at the startup of server. Only records from the configured projects will be exported. Existing data in the Kafka topics will be deleted before publishing new records to avoid duplication.</td>
        <td> false </td>
     </tr>
    <tr>
        <td> redcap.projects.filePath </td>
        <td> Path to the file where the configuration of REDCap projects are read. Each project configuration should include the project id and API token. For example:
     
 ```json
 [
    {    
        id = "<PROJECT_ID>"    
        token = "<PROJECT_TOKEN>" 
    }
]
 ```
 </td>
<td> redcap-projects.json </td>
 </tr>
</table>
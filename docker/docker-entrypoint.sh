#!/usr/bin/env bash

JAVA_CMD="java -Xms256m -Xmx3g -jar "

# Configure application.conf path
if [ ! -z "$APP_CONF_FILE" ]; then
    JAVA_CMD+="-Dconfig.file=$APP_CONF_FILE "
fi

# Configure FHIR repository server binding host
if [ ! -z "$SERVER_HOST" ]; then
    JAVA_CMD+="-Dwebserver.host=$SERVER_HOST "
fi
if [ ! -z "$SERVER_PORT" ]; then
    JAVA_CMD+="-Dwebserver.port=$SERVER_PORT "
fi
if [ ! -z "$SERVER_BASE_URI" ]; then
    JAVA_CMD+="-Dwebserver.base-uri=$SERVER_BASE_URI "
fi

# Configure SSL
if [ ! -z "$SSL_KEYSTORE" ]; then
    JAVA_CMD+="-Dwebserver.ssl.keystore=$SSL_KEYSTORE "
fi
if [ ! -z "$SSL_PASSWORD" ]; then
    JAVA_CMD+="-Dwebserver.ssl.password=$SSL_PASSWORD "
fi

# Configure Kafka broker
if [ ! -z "$KAFKA_BOOTSTRAP_SERVERS" ]; then
    JAVA_CMD+="-Dkafka.bootstrap-servers=$KAFKA_BOOTSTRAP_SERVERS "
fi

# Configure REDCap
if [ ! -z "$REDCAP_URL" ]; then
    JAVA_CMD+="-Dredcap.url=$REDCAP_URL "
fi

# Delay the execution for this amount of seconds
if [ ! -z "$DELAY_EXECUTION" ]; then
    sleep $DELAY_EXECUTION
fi

# Finally, tell which jar to run
JAVA_CMD+="tofhir-redcap-standalone.jar"

eval $JAVA_CMD

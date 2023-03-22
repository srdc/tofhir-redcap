# Stage 0
FROM maven:3.6-openjdk-11 AS builder

ENV TOFHIR_REDCAP_HOME /usr/local/tofhir-redcap
RUN mkdir -p "$TOFHIR_REDCAP_HOME"
WORKDIR $TOFHIR_REDCAP_HOME

COPY . ./

# Stage 1
FROM openjdk:11-jre-slim

# install dos2unix
RUN apt -y update && apt install -y dos2unix

ENV TOFHIR_REDCAP_HOME /usr/local/tofhir-redcap
RUN mkdir -p "$TOFHIR_REDCAP_HOME"
WORKDIR $TOFHIR_REDCAP_HOME

COPY --from=builder $TOFHIR_REDCAP_HOME/tofhir-redcap-standalone.jar .

COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

# Convert line endings and finally remove dos2unix
RUN dos2unix ./docker-entrypoint.sh && apt-get --purge remove -y dos2unix && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["./docker-entrypoint.sh"]

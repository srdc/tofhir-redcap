FROM openjdk:11-jre-slim

ENV TOFHIR_HOME /usr/local/tofhir-redcap
RUN mkdir -p "$TOFHIR_HOME"
WORKDIR $TOFHIR_HOME

COPY ./target/tofhir-redcap-standalone.jar .
COPY ./docker/docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

ENTRYPOINT ["./docker-entrypoint.sh"]

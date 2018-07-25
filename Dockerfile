FROM folioci/openjdk8-jre:latest
MAINTAINER fsadiq1@jhu.edu

USER root
RUN mkdir /etc/folio

USER folio
ENV VERTICLE_FILE mod-oriole-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}


# Expose this port locally in the container.
EXPOSE 8081

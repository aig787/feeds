FROM adoptopenjdk:15-jdk AS build

RUN apt-get update && apt-get upgrade -y && apt-get install -y git tree

WORKDIR build
# Copy gradle resources
COPY build.gradle.kts gradle.properties settings.gradle.kts gradlew ./
RUN mkdir output storage test-utils data
COPY gradle ./gradle
COPY buildSrc ./buildSrc
# Build deps
RUN ./gradlew build -x test
# Copy everything else
COPY . .
# Build artifact
RUN ./gradlew distTar
RUN ./gradlew :showVersion
RUN mv build/distributions/feeds-$(./gradlew :showVersion -q -Prelease.quiet | cut -d' ' -f2).tar dist.tar

FROM adoptopenjdk/openjdk11:alpine-jre
ENV CLASSPATH=""
ENV JAVA_OPTS=""
ENV FEEDS_OPTS="-Dconfig.file=/opt/feeds/conf/application.conf"

WORKDIR /opt/feeds
COPY --from=build build/dist.tar .
RUN tar xvf dist.tar --strip-components=1
RUN rm dist.tar

ENTRYPOINT /opt/feeds/bin/feeds

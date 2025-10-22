FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY project/build.properties project/
COPY project/plugins.sbt project/
COPY project/BuildCommands.scala project/

RUN apt-get update && apt-get install -y curl && \
    curl -L https://github.com/sbt/sbt/releases/download/v1.11.5/sbt-1.11.5.tgz | tar xz -C /usr/local && \
    ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt

COPY build.sbt .
COPY .scalafmt.conf .
COPY scalastyle-config.xml .

RUN sbt update

COPY src src/

RUN sbt stage

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/universal/stage .

EXPOSE 9000

ENV APPLICATION_SECRET="changeme"

CMD ["bin/skatemap-live"]

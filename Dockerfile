FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY project/build.properties project/
COPY project/plugins.sbt project/
COPY project/build.sbt project/
COPY project/BuildCommands.scala project/

RUN apt-get update && apt-get install -y curl && \
    curl -L https://github.com/sbt/sbt/releases/download/v1.11.5/sbt-1.11.5.tgz | tar xz -C /usr/local && \
    ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt

COPY build.sbt .
COPY .scalafmt.conf .
COPY scalastyle-config.xml .
COPY src src/

RUN sbt stage

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/universal/stage .

EXPOSE 9000

ENV APPLICATION_SECRET="b8b7567764ee6289aa81c909e90fbf4b190ee0ba8ec5dfb9fba2f8a3429d9c29"

CMD bin/skatemap-live -Dplay.http.secret.key=${APPLICATION_SECRET}

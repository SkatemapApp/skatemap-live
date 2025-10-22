# syntax=docker/dockerfile:1.4
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

RUN --mount=type=cache,target=/root/.sbt \
    --mount=type=cache,target=/root/.ivy2 \
    --mount=type=cache,target=/root/.cache/coursier \
    sbt stage

FROM eclipse-temurin:21-jre

RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r skatemap && \
    useradd -r -g skatemap skatemap

WORKDIR /app

COPY --from=builder /app/target/universal/stage .

RUN chown -R skatemap:skatemap /app

USER skatemap

EXPOSE 9000

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9000/health || exit 1

CMD if [ -z "$APPLICATION_SECRET" ]; then \
      echo "ERROR: APPLICATION_SECRET environment variable is required" && exit 1; \
    fi && \
    bin/skatemap-live -Dplay.http.secret.key=${APPLICATION_SECRET}

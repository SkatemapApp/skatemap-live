# syntax=docker/dockerfile:1.4
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY services/api/project/build.properties project/
COPY services/api/project/plugins.sbt project/
COPY services/api/project/build.sbt project/
COPY services/api/project/BuildCommands.scala project/

RUN apt-get update && apt-get install -y curl && \
    curl -L https://github.com/sbt/sbt/releases/download/v1.11.5/sbt-1.11.5.tgz | tar xz -C /usr/local && \
    ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt

COPY services/api/build.sbt .
COPY services/api/.scalafmt.conf .
COPY services/api/scalastyle-config.xml .
COPY services/api/src src/

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
COPY services/api/docker-entrypoint.sh /app/

RUN chown -R skatemap:skatemap /app && \
    chmod +x /app/docker-entrypoint.sh

USER skatemap

EXPOSE 9000

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9000/health || exit 1

CMD ["/app/docker-entrypoint.sh"]

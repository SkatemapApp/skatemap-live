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

RUN --mount=type=cache,id=skatemap-sbt,target=/root/.sbt \
    --mount=type=cache,id=skatemap-ivy2,target=/root/.ivy2 \
    --mount=type=cache,id=skatemap-coursier,target=/root/.cache/coursier \
    sbt stage

FROM eclipse-temurin:21-jre

RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r skatemap && \
    useradd -r -g skatemap skatemap

WORKDIR /app

COPY --from=builder /app/target/universal/stage .
COPY docker-entrypoint.sh /app/

RUN chown -R skatemap:skatemap /app && \
    chmod +x /app/docker-entrypoint.sh

USER skatemap

EXPOSE 9000

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9000/health || exit 1

CMD ["/app/docker-entrypoint.sh"]

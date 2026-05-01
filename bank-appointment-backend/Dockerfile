FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
ARG QUARKUS_BUILD_PROFILE=prod

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -Dquarkus.profile="${QUARKUS_BUILD_PROFILE}" package

FROM eclipse-temurin:21-jre-jammy AS runtime

ENV LANG=C.UTF-8 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    QUARKUS_HTTP_HOST=0.0.0.0 \
    QUARKUS_HTTP_PORT=8080

RUN groupadd --system app && useradd --system --gid app --no-create-home --uid 1001 app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/target/quarkus-app/lib/      ./lib/
COPY --from=build --chown=app:app /workspace/target/quarkus-app/*.jar      ./
COPY --from=build --chown=app:app /workspace/target/quarkus-app/app/      ./app/
COPY --from=build --chown=app:app /workspace/target/quarkus-app/quarkus/  ./quarkus/
COPY --chown=app:app docker/entrypoint.sh ./entrypoint.sh
RUN chmod 500 /app/entrypoint.sh

USER 1001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/q/health/live || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]

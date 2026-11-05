# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -DskipTests package \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination /workspace/layers

FROM eclipse-temurin:24-jre-alpine
RUN apk upgrade --no-cache && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/layers/dependencies/ ./
COPY --from=build --chown=app:app /workspace/layers/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/layers/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/layers/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

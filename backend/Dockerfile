# syntax=docker/dockerfile:1
# One Dockerfile for every backend app. Pick the app with --build-arg MODULE=<path> and its port with PORT, e.g.
#   docker build --build-arg MODULE=services/booking-service --build-arg PORT=8082 -t gridreserve/booking-service backend/
# Multi-platform (M1.10): the build stage always runs on the builder's own CPU ($BUILDPLATFORM), because a jar
# is the same on every CPU; only the small runtime stage is built per platform (amd64 laptops, arm64 Oracle).

# ---- build stage: compile only the chosen module and the modules it depends on ----
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY libs libs
COPY platform platform
COPY services services
# The parent pom lists system-tests; Maven needs its pom to load the build, never its tests.
COPY system-tests/pom.xml system-tests/pom.xml
ARG MODULE
# The Maven cache is shared between image builds (and locked so parallel builds don't collide).
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q -pl "${MODULE}" -am package -DskipTests \
 && cp "${MODULE}"/target/*.jar /workspace/app.jar

# ---- runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app
ARG PORT
ENV HEALTH_PORT=${PORT}
EXPOSE ${PORT}
# Memory budget per service (A-28): heap 256 MB plus bounded non-heap areas, so ~5 JVMs fit next to PostgreSQL.
# Serial GC: the smallest collector, fine for small heaps and few cores. Exit on OutOfMemoryError so the
# container platform restarts a broken JVM instead of leaving it half alive. Compose may override this.
ENV JAVA_TOOL_OPTIONS="-Xmx256m -Xss512k -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
# For runtimes without their own check (Compose defines the same one). Readiness: up and able to serve.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fs "http://localhost:${HEALTH_PORT}/actuator/health/readiness" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

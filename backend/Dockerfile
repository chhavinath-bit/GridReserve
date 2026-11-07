# syntax=docker/dockerfile:1
# One Dockerfile for every backend app. Pick the app with --build-arg MODULE=<path>, e.g.
#   docker build --build-arg MODULE=services/booking-service -t gridreserve/booking-service backend/
# Hardened for production in M1.10.

# ---- build stage: compile only the chosen module and the modules it depends on ----
FROM eclipse-temurin:21-jdk AS build
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
# Heap and other JVM flags come from JAVA_TOOL_OPTIONS (Compose sets -Xmx256m, A-28).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

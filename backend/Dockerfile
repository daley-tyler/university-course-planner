# ───────────────────────────────
#  Stage 1: Build
# ───────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and project descriptor
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Prepare the wrapper; package resolves only the dependencies this build uses.
RUN chmod +x mvnw

# Copy source and build the jar
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests -B

# ───────────────────────────────
#  Stage 2: Runtime
# ───────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar from previous stage
COPY --from=build /app/target/*.jar app.jar

# Use a non-root user for safety
RUN addgroup -S spring && adduser -S spring -G spring
RUN mkdir -p /app/logs && chown spring:spring /app/logs
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# Resolve dependencies first so they are cached until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/*.jar app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S minhaj && adduser -S -G minhaj minhaj

WORKDIR /app

COPY --from=build --chown=minhaj:minhaj /workspace/app.jar app.jar

USER minhaj

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

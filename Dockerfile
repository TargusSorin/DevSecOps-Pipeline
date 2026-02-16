# ── Build stage ──────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline --batch-mode --no-transfer-progress

COPY src src
RUN ./mvnw package -DskipTests --batch-mode --no-transfer-progress

# ── Runtime stage ────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

RUN groupadd --system appuser && useradd --system --gid appuser appuser

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

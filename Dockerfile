FROM maven:3.9-eclipse-temurin-26-alpine AS construccion
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN adduser -S marketplace
USER marketplace
COPY --from=construccion /app/target/marketplace-api.jar /app/marketplace-api.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/marketplace-api.jar"]

# La EC2 es ARM (t4g). Maven corre en la plataforma de quien construye y solo la imagen final es de la plataforma
# destino, así que el CI construye para linux/arm64 sin emular la compilación.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21-alpine AS construccion
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN adduser -S marketplace
# Los archivos de despliegue viajan con la imagen. El documento de SSM los copia a /opt/marketplace antes de
# desplegar, así que el servidor siempre usa los de la misma versión.
COPY deploy /despliegue
USER marketplace
COPY --from=construccion /app/target/marketplace-api.jar /app/marketplace-api.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/marketplace-api.jar"]

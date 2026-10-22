# Build the Spring Boot application with Java 21, then run it on a smaller Java 21 image.
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/bharatgo-api-1.0.0.jar /app/app.jar
USER app
ENV SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=35s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/api/health || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]

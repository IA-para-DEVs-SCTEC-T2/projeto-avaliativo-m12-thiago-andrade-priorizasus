FROM maven:3.9.9-eclipse-temurin-22 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src src

RUN mvn -DskipTests -Dspotless.check.skip=true package

FROM eclipse-temurin:22-jre-jammy

WORKDIR /app

RUN useradd -r -u 1001 appuser

COPY --from=build /workspace/target/priorizasus-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENV SERVER_PORT=8080

USER 1001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
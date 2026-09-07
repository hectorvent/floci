FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build
COPY sidecars/cedar/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY sidecars/cedar/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/target/floci-cedar-sidecar-1.0.0.jar /app/sidecar.jar
COPY --from=build /build/target/dependency /app/lib
EXPOSE 8180
ENTRYPOINT ["java", "-cp", "/app/sidecar.jar:/app/lib/*", "io.github.hectorvent.floci.cedar.CedarSidecarServer"]

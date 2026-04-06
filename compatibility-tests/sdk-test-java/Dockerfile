FROM eclipse-temurin:17-jdk
WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src/ src/

ENV FLOCI_ENDPOINT=http://floci:4566

RUN mkdir -p /results
ENTRYPOINT ["/bin/bash", "-c", "mvn test -q && cp -r target/surefire-reports/*.xml /results/ 2>/dev/null || true"]
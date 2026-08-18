# Multi-stage Java 25 LTS / Spring Boot build.
# Keep EXPOSE 8080 for smoke and compose.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

ARG MAVEN_VERSION=3.9.11
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates tar \
    && curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        | tar -xz -C /opt \
    && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY .openapi-generator-ignore .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=build /src/target/gym-buddy-service.jar /app/app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

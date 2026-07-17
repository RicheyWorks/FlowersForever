# FlowersForever — headless REST-only image (no Swing GUI; see ADR-001)
# Build:  docker build -t flowersforever .
# Run:    docker run -p 8080:8080 -v flowerfarm-data:/app/data flowersforever

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
RUN useradd --system --home /app flowerfarm
WORKDIR /app
COPY --from=build /build/target/flowerfarm-manager-*.jar app.jar
RUN mkdir -p /app/data && chown -R flowerfarm /app
USER flowerfarm
VOLUME /app/data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]

# ============================================================
# Stage 1: Cache Maven dependencies (rarely changes)
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS deps
WORKDIR /app

# Copy ONLY pom.xml + wrapper first — this layer is cached unless pom.xml changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download all dependencies offline (cached layer)
RUN ./mvnw dependency:go-offline -B

# ============================================================
# Stage 2: Build the application
# ============================================================
FROM deps AS build
WORKDIR /app

# Copy source code (this invalidates cache only when src changes)
COPY src ./src

# Build the JAR (dependencies already cached from Stage 1)
RUN ./mvnw package -DskipTests -B

# ============================================================
# Stage 3: Minimal runtime image
# ============================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup


# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# JVM tuning for containers: SerialGC fits <1 CPU budget better than G1GC
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:+UseSerialGC", \
    "-XX:MaxRAMPercentage=50.0", \
    "-XX:InitialRAMPercentage=25.0", \
    "-XX:MaxMetaspaceSize=128m", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Xss256k", \
    "-Djava.awt.headless=true", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.jmx.enabled=false", \
    "-Djava.net.preferIPv4Stack=true", \
    "-jar", "app.jar"]

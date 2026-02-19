# ============================================================
# Stage 1: Cache Maven dependencies (rarely changes)
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS deps
WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy ONLY pom.xml first — this layer is cached unless pom.xml changes
COPY pom.xml .

# Download all dependencies offline (cached layer)
RUN mvn dependency:go-offline -B

# ============================================================
# Stage 2: Build the application
# ============================================================
FROM deps AS build
WORKDIR /app

# Copy source code (this invalidates cache only when src changes)
COPY src ./src

# Build the JAR (dependencies already cached from Stage 1)
RUN mvn package -DskipTests -B

# ============================================================
# Stage 3: Minimal runtime image
# ============================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create uploads directory
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app/uploads

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# JVM tuning for containers — faster startup, lower memory
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+TieredCompilation", \
    "-XX:TieredStopAtLevel=1", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

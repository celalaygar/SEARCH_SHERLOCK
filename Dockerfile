# 1. Build aşaması (Multi-stage build)
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy files
COPY . .

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application
RUN ./gradlew clean bootJar -x test

# 2. Runtime aşaması
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/search-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 7111
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

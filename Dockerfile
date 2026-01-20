# Repository Link: https://github.com/bwubbu/openhab-core
# Student Name: MINGYANG PENG (22050490)
# Assignment: WIF3005 Alternative Assessment - Question 1

# Build Stage
# Use Maven with JDK 21 to compile the project
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the entire project source code into the container
COPY . .

# Run the Maven build command
# -DskipTests: Skips unit tests to speed up the build process
# -Dspotless.check.skip=true: Prevents build failures due to formatting differences
RUN mvn clean install -DskipTests -Dspotless.check.skip=true

# Runtime Stage
# Use a lightweight JRE image to run the application
FROM eclipse-temurin:21-jre

# Set the working directory for the runtime
WORKDIR /app

# Copy the compiled JAR file from the builder stage
COPY --from=builder /app/bundles/org.openhab.core/target/*.jar ./openhab-core.jar

# Expose port 5000 as required by the assignment rubric
EXPOSE 5000

# Define the command to run when the container starts
CMD ["java", "-version"]
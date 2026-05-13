FROM maven:3.9.15-eclipse-temurin-25-alpine AS build

# Set the working directory
WORKDIR /build

# Copy only the POM file first
COPY pom.xml .

# Download dependencies - this layer will be cached as long as pom.xml doesn't change
RUN mvn dependency:go-offline -q

# Copy source files
COPY src/ ./src/

# Copy any other necessary project files
COPY ["*.xml", "./"]
# Copy properties files if they exist
COPY *.properties ./

# Build the jar
RUN mvn package -q -T2C

FROM eclipse-temurin:25-jre-alpine

# Set the working directory
WORKDIR /home/container

# Copy the built jar from the build stage
COPY --from=build /build/target/Minecraft-Utilities.jar target/Minecraft-Utilities.jar

# Make port 80 available to the world outside this container
EXPOSE 80
ENV PORT=80

# Indicate that we're running in production
ENV ENVIRONMENT=production

# Start the application
CMD ["java",
  "-XX:MaxRAMPercentage=75.0",
  "-XX:+UseZGC",
  "-XX:+ZGenerational",
  "-Djava.awt.headless=true",
  "-jar", "target/Minecraft-Utilities.jar"
]
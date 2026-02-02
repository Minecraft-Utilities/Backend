FROM maven:3.9.12-eclipse-temurin-25-alpine

# Set the working directory
WORKDIR /home/container

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

# Make port 80 available to the world outside this container
EXPOSE 80
ENV PORT=80

# Indicate that we're running in production
ENV ENVIRONMENT=production

# Run the jar file using JSON format for CMD (addressing the warning)
CMD ["java", "-jar", "target/Minecraft-Utilities.jar", "-Djava.awt.headless=true"]
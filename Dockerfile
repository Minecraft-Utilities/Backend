FROM maven:3.9.8-eclipse-temurin-17-alpine

# Install font dependencies
RUN apk --update --upgrade --no-cache add fontconfig ttf-freefont font-noto terminus-font \
     && fc-cache -f \
     && fc-list | sort

# Set the working directory
WORKDIR /home/container

# Copy only the POM file first
COPY pom.xml .

# Download dependencies - this layer will be cached as long as pom.xml doesn't change
RUN mvn dependency:go-offline -q

# Copy source files
COPY src/ ./src/

# Copy any other necessary project files (excluding what's in .dockerignore)
COPY *.xml *.properties ./ 2>/dev/null || true

# Build the jar
RUN mvn package -q -Dmaven.test.skip -DskipTests -T2C

# Make port 80 available to the world outside this container
EXPOSE 80
ENV PORT=80

# Indicate that we're running in production
ENV ENVIRONMENT=production

# Run the jar file
CMD java -jar target/Minecraft-Utilities.jar -Djava.awt.headless=true
# Minecraft Utilities - Backend

API for Minecraft player data (skins, capes, profiles), Java/Bedrock server status and previews.

## Users

Use the API at [mc.fascinated.cc/api](https://mc.fascinated.cc/api).

## Prerequisites

- Java 25
- Redis
- S3-compatible storage (e.g. RustFS)

Optional: MaxMind license.

## Configuration

On first run without `application.yml` in the working directory, the app copies the default config and exits. Edit it (Redis, S3, `public-url`, and optionally MaxMind, `metrics-token`) and run again.

## Running

**Local:** `mvn package` then `java -jar target/Minecraft-Utilities.jar` (with `application.yml` in the current directory).

**Docker:** `docker build -t mcutils-backend .` then run the image; mount or override config as needed (e.g. bind-mount `application.yml`).

## API docs

API documentation is available [here](https://mc.fascinated.cc/api/swagger-ui.html) or at `/swagger-ui.html` when running locally.

## License

See [LICENSE](LICENSE).

# Minecraft Utilities - Backend

Backend API for Minecraft player data (skins, capes, profiles), Java/Bedrock server status and previews, with Redis caching and S3-compatible storage. Optional MaxMind GeoIP.

## Users

Use the API at [mc.fascinated.cc/api](https://mc.fascinated.cc/api).

## Prerequisites

- Java 25
- Redis
- S3-compatible storage (e.g. MinIO)

Optional: MaxMind license.

## Configuration

On first run without `application.yml` in the working directory, the app copies the default config and exits. Edit it (Redis, S3, `public-url`, and optionally MaxMind, `metrics-token`) and run again.

## Running

**Local:** `mvn package` then `java -jar target/Minecraft-Utilities.jar` (with `application.yml` in the current directory).

**Docker:** `docker build -t mcutils-backend .` then run the image; mount or override config as needed (e.g. bind-mount `application.yml`).

## Font rendering (server preview)

Server preview text uses a Minecraft-style bitmap font. Advance (character spacing) is measured from the texture by default. For **exact Minecraft spacing**, add a `default_widths.json` file in `src/main/resources/font/` in [mc-fonts format](https://github.com/Owen1212055/mc-fonts) (keys: `missing_char`, `chars` with per-character `width`). You can copy the default font JSON from that repo into `font/default_widths.json` so each character uses Minecraft’s advance values.

## API docs

API documentation is available [here](https://mc.fascinated.cc/api/swagger-ui.html) or at `/swagger-ui.html` when running locally.

## License

See [LICENSE](LICENSE).

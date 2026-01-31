package xyz.mcutils.backend.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.exception.impl.BadRequestException;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

@UtilityClass @Log4j2(topic = "Player Utils")
public class PlayerUtils {

    /**
     * Gets the UUID from the string.
     *
     * @param id the id string
     * @return the UUID
     */
    public static UUID getUuidFromString(String id) {
        UUID uuid;
        boolean isFullUuid = id.length() == 36;
        if (id.length() == 32 || isFullUuid) {
            try {
                uuid = isFullUuid ? UUID.fromString(id) : UUIDUtils.addDashes(id);
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Invalid UUID provided: '%s'".formatted(id));
            }
            return uuid;
        }
        return null;
    }

    /**
     * Gets the image data from the URL.
     *
     * @return the image data
     */
    @SneakyThrows @JsonIgnore
    public static byte[] getImage(String url) {
        HttpResponse<byte[]> response = Constants.HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return null;
    }
}

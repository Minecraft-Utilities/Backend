package xyz.mcutils.backend.model.server;

import com.maxmind.geoip2.model.CityResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The location of the server.
 */
@AllArgsConstructor
@Getter
public class GeoLocation {
    /**
     * The country of the server.
     */
    private final String country;

    /**
     * The country code for the country.
     */
    private final String countryCode;

    /**
     * The region of the server.
     */
    private final String region;

    /**
     * The city of the server.
     */
    private final String city;

    /**
     * The latitude of the server.
     */
    private final double latitude;

    /**
     * The longitude of the server.
     */
    private final double longitude;

    /**
     * Gets the location of the server from Maxmind.
     *
     * @param response the response from Maxmind
     * @return the location of the server
     */
    public static GeoLocation fromMaxMind(CityResponse response) {
        if (response == null) {
            return null;
        }
        return new GeoLocation(
                response.country().name(),
                response.country().isoCode(),
                response.mostSpecificSubdivision().name(),
                response.city().name(),
                response.location().latitude(),
                response.location().longitude()
        );
    }
}

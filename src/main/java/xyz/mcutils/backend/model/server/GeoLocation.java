package xyz.mcutils.backend.model.server;

import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
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
     * Direct link to a url for the country flag;
     */
    private final String flagUrl;

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
        Country country = response.country();
        Location location = response.location();
        String isoCode = country.isoCode();

        return new GeoLocation(
                country.name(),
                isoCode,
                response.mostSpecificSubdivision().name(),
                response.city().name(),
                location.latitude(),
                location.longitude(),
                "https://flagcdn.com/w20/" + isoCode.toLowerCase() + ".webp"
        );
    }
}

package xyz.mcutils.backend.model.server;

import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;

/**
 * The location of the server.
 *
 * @param country     The country of the server.
 * @param countryCode The country code for the country.
 * @param region      The region of the server.
 * @param city        The city of the server.
 * @param latitude    The latitude of the server.
 * @param longitude   The longitude of the server.
 * @param flagUrl     Direct link to a url for the country flag;
 */
public record GeoLocation(String country, String countryCode, String region, String city, double latitude,
                          double longitude, String flagUrl) {
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

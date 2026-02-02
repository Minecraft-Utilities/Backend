package xyz.mcutils.backend.model.geo;

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
                          double longitude, String flagUrl) { }

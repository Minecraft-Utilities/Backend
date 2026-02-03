package xyz.mcutils.backend.model.response;

import xyz.mcutils.backend.model.asn.AsnLookup;
import xyz.mcutils.backend.model.geo.GeoLocation;

/**
 * A record that contains the IP lookup information.
 *
 * @param ip the IP address
 * @param reverseDns the reverse DNS of the IP address
 * @param location the location of the IP address
 * @param asn the ASN of the IP address
 */
public record IpLookup(String ip, String reverseDns, GeoLocation location, AsnLookup asn) { }
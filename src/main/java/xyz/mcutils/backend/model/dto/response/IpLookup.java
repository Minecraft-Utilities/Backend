package xyz.mcutils.backend.model.dto.response;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.model.domain.asn.AsnLookup;
import xyz.mcutils.backend.model.domain.geo.GeoLocation;

/**
 * A record that contains the IP lookup information.
 *
 * @param ip the IP address
 * @param location the location of the IP address
 * @param asn the ASN of the IP address
 */
public record IpLookup(String ip, @Nullable GeoLocation location, @Nullable AsnLookup asn) { }

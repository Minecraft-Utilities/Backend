package xyz.mcutils.backend.model.response;

import xyz.mcutils.backend.model.asn.AsnLookup;
import xyz.mcutils.backend.model.geo.GeoLocation;

public record IpLookup(String ip, GeoLocation location, AsnLookup asn) { }
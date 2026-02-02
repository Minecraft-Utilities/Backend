package xyz.mcutils.backend.model.asn;

/**
 * The ASN information for this server.
 *
 * @param asn    The ASN number.
 * @param asnOrg The name of the Organization who owns the ASN.
 * @param cidrBlock The CIDR block of the ASN.
 */
public record AsnLookup(String asn, String asnOrg, String cidr) { }
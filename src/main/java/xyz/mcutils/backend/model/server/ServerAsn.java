package xyz.mcutils.backend.model.server;

import com.maxmind.geoip2.model.AsnResponse;

/**
 * The ASN information for this server.
 *
 * @param asn    The ASN number.
 * @param asnOrg The name of the Organization who owns the ASN.
 */
public record ServerAsn(String asn, String asnOrg) {
    /**
     * Gets the location of the server from Maxmind.
     *
     * @param response the response from Maxmind
     * @return the location of the server
     */
    public static ServerAsn fromMaxMind(AsnResponse response) {
        if (response == null) {
            return null;
        }
        return new ServerAsn(
                "AS%s".formatted(response.autonomousSystemNumber()),
                response.autonomousSystemOrganization()
        );
    }
}
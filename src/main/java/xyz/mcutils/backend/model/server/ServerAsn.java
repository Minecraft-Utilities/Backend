package xyz.mcutils.backend.model.server;

import com.maxmind.geoip2.model.AsnResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The ASN information for this server.
 */
@AllArgsConstructor
@Getter
public class ServerAsn {
    /**
     * The ASN number.
     */
    private final String asn;

    /**
     * The name of the Organization who owns the ASN.
     */
    private final String asnOrg;

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
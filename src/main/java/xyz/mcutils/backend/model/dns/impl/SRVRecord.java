package xyz.mcutils.backend.model.dns.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import xyz.mcutils.backend.model.dns.DNSRecord;

import java.net.InetSocketAddress;

@Setter @Getter
@NoArgsConstructor
public final class SRVRecord extends DNSRecord {
    /**
     * The name of this record.
     */
    @NonNull private String name;

    /**
     * The target of this record.
     */
    @NonNull private String target;

    /**
     * The priority of this record.
     */
    private int priority;

    /**
     * The weight of this record.
     */
    private int weight;

    /**
     * The port of this record.
     */
    private int port;

    public SRVRecord(@NonNull org.xbill.DNS.SRVRecord bootstrap) {
        super(Type.SRV, bootstrap.getTTL());
        this.name = bootstrap.getName().toString().replaceFirst("\\.$", "");
        this.target = bootstrap.getTarget().toString().replaceFirst("\\.$", "");
        this.priority = bootstrap.getPriority();
        this.weight = bootstrap.getWeight();
        this.port = bootstrap.getPort();
    }

    /**
     * Get a socket address from
     * the target and port.
     *
     * @return the socket address
     */
    @NonNull @JsonIgnore
    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(this.target, this.port);
    }
}
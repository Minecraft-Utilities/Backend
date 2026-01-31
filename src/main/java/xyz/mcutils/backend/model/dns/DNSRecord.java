package xyz.mcutils.backend.model.dns;


import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Setter @Getter @EqualsAndHashCode
public abstract class DNSRecord {
    /**
     * The type of this record.
     */
    @NonNull
    private Type type;

    /**
     * The TTL (Time To Live) of this record.
     */
    private long ttl;

    /**
     * Types of a record.
     */
    public enum Type {
        A, SRV
    }
}

package xyz.mcutils.backend.model.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import xyz.mcutils.backend.common.CachedResponse;
import xyz.mcutils.backend.model.response.IpLookup;

import java.io.Serializable;

@Setter @Getter @EqualsAndHashCode(callSuper = false)
@RedisHash(value = "ipLookup", timeToLive = 60L * 60L * 24L) // 1 day
public class CachedIpLookup extends CachedResponse implements Serializable {
    /**
     * The id of this ip lookup.
     */
    @Id
    @NonNull
    @JsonIgnore
    private String id;

    /**
     * The cached ip lookup.
     */
    @NonNull @JsonUnwrapped
    private IpLookup ipLookup;

    public CachedIpLookup(@NonNull String id, @NonNull IpLookup ipLookup) {
        super(false, System.currentTimeMillis());
        this.id = id;
        this.ipLookup = ipLookup;
    }
}
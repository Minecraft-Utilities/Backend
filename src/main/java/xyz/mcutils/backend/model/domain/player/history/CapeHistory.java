package xyz.mcutils.backend.model.domain.player.history;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;

import java.time.Instant;

@NoArgsConstructor
@Getter
@Slf4j
public class CapeHistory extends VanillaCape {

    /**
     * The date this cape was last seen on the player.
     */
    private Instant lastUsed;

    public CapeHistory(VanillaCape cape, Instant lastUsed) {
        super(cape.getId(), cape.getName(), cape.getTextureId(), cape.getUniqueOwners());
        this.lastUsed = lastUsed;
    }
}

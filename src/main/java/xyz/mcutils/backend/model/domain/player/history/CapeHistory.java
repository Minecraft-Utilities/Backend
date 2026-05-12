package xyz.mcutils.backend.model.domain.player.history;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;

import java.util.Date;

@NoArgsConstructor
@Getter
@Slf4j
public class CapeHistory extends VanillaCape {
    /**
     * The date this cape was first seen on the player.
     */
    private Date firstSeen;

    /**
     * The date this cape was last seen on the player.
     */
    private Date lastUsed;

    public CapeHistory(VanillaCape cape, Date firstSeen, Date lastUsed) {
        super(cape.getUuid(), cape.getName(), cape.getAccountsOwned(), cape.getTextureId());
        this.firstSeen = firstSeen;
        this.lastUsed = lastUsed;
    }
}

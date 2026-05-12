package xyz.mcutils.backend.model.domain.player.history;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.util.Date;

@NoArgsConstructor
@Getter
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class SkinHistory extends Skin {
    /**
     * The date this skin was first seen on the player.
     */
    private Date firstSeen;

    /**
     * The date this skin was last seen on the player.
     */
    private Date lastUsed;

    public SkinHistory(Skin skin, Date firstSeen, Date lastUsed) {
        super(skin.getUuid(), skin.getTextureId(), skin.getModel(), skin.isLegacy());
        this.firstSeen = firstSeen;
        this.lastUsed = lastUsed;
    }
}

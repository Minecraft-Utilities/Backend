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
     * The date this skin was first used by the player.
     */
    private Date firstUsed;

    /**
     * The date this skin was last used by the player.
     */
    private Date lastUsed;

    public SkinHistory(Skin skin, Date firstUsed, Date lastUsed) {
        super(skin.getUuid(), skin.getTextureId(), skin.getModel(), skin.isLegacy());
        this.firstUsed = firstUsed;
        this.lastUsed = lastUsed;
    }
}

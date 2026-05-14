package xyz.mcutils.backend.model.domain.player.history;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.time.Instant;

@NoArgsConstructor
@Getter
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class SkinHistory extends Skin {
    /**
     * The date this skin was last seen on the player.
     */
    private Instant lastUsed;

    public SkinHistory(Skin skin, Instant lastUsed) {
        super(skin.getId(), skin.getTextureId(), skin.getModel(), skin.getUniqueOwners(), skin.isLegacy());
        this.lastUsed = lastUsed;
    }
}

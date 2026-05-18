package xyz.mcutils.backend.repository.postgres;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Chunked JDBC batch inserts for player skin/cape adoptions.
 */
@Component
public class PlayerAdoptionBatchWriter {
    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;

    private static final String INSERT_FIRST_SKIN = """
            INSERT INTO player_skin_adoptions (player_id, skin_id, first_seen, last_equipped_at)
            VALUES (?, ?, ?, NULL)
            ON CONFLICT (player_id, skin_id) DO NOTHING
            """;

    private static final String INSERT_FIRST_CAPE = """
            INSERT INTO player_cape_adoptions (player_id, cape_id, first_seen, last_equipped_at)
            VALUES (?, ?, ?, NULL)
            ON CONFLICT (player_id, cape_id) DO NOTHING
            """;

    private static final String RECORD_SKIN_EQUIP = """
            INSERT INTO player_skin_adoptions (player_id, skin_id, first_seen, last_equipped_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (player_id, skin_id) DO UPDATE
                SET last_equipped_at = EXCLUDED.last_equipped_at
            """;

    private static final String RECORD_CAPE_EQUIP = """
            INSERT INTO player_cape_adoptions (player_id, cape_id, first_seen, last_equipped_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (player_id, cape_id) DO UPDATE
                SET last_equipped_at = EXCLUDED.last_equipped_at
            """;

    public PlayerAdoptionBatchWriter(JdbcTemplate jdbcTemplate,
                                   @Value("${spring.jpa.properties.hibernate.jdbc.batch_size}") int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = batchSize;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertFirstSkinAdoptions(List<PlayerAssetAdoption> adoptions, Instant timestamp) {
        batch(adoptions, timestamp, INSERT_FIRST_SKIN, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertFirstCapeAdoptions(List<PlayerAssetAdoption> adoptions, Instant timestamp) {
        batch(adoptions, timestamp, INSERT_FIRST_CAPE, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSkinEquips(List<PlayerAssetAdoption> adoptions, Instant timestamp) {
        batch(adoptions, timestamp, RECORD_SKIN_EQUIP, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCapeEquips(List<PlayerAssetAdoption> adoptions, Instant timestamp) {
        batch(adoptions, timestamp, RECORD_CAPE_EQUIP, true);
    }

    private void batch(List<PlayerAssetAdoption> adoptions, Instant timestamp, String sql, boolean setLastEquipped) {
        if (adoptions.isEmpty()) {
            return;
        }
        Timestamp ts = Timestamp.from(timestamp);
        for (int offset = 0; offset < adoptions.size(); offset += this.batchSize) {
            List<PlayerAssetAdoption> chunk = adoptions.subList(offset, Math.min(offset + this.batchSize, adoptions.size()));
            jdbcTemplate.batchUpdate(sql, chunk, chunk.size(), (ps, adoption) -> {
                ps.setObject(1, adoption.playerId());
                ps.setLong(2, adoption.assetId());
                ps.setTimestamp(3, ts);
                if (setLastEquipped) {
                    ps.setTimestamp(4, ts);
                }
            });
        }
    }
}

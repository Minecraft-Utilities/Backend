package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "capes")
@Getter
@NoArgsConstructor
public class CapeRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String textureId;

    @Column(nullable = false, name = "unique_owners")
    private long uniqueOwners;

    @Column(nullable = false, name = "first_seen")
    private Instant firstSeen;

    @Column(name = "first_seen_using_player_id")
    private UUID firstSeenUsingPlayerId;

    public CapeRow(String name, String textureId, long uniqueOwners, Instant firstSeen) {
        this.name = name;
        this.textureId = textureId;
        this.uniqueOwners = uniqueOwners;
        this.firstSeen = firstSeen;
    }
}

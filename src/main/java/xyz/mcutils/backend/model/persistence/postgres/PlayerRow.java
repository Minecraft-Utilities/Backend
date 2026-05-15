package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

@Entity
@Table(name = "players")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerRow {
    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private boolean legacyAccount;

    @Column(nullable = false, name = "submitted_uuids")
    private long submittedUuids;

    @ManyToOne
    @JoinColumn(nullable = false, name = "skin_id")
    private SkinRow skin;

    @ManyToOne
    @JoinColumn(name = "cape_id")
    @Nullable
    private CapeRow cape;

    @Column(nullable = false)
    private Instant lastUpdated;

    @Column(nullable = false)
    private Instant firstSeen;
}

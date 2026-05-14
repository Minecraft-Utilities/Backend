package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.time.Instant;

@Entity
@Table(name = "skins")
@Getter
@NoArgsConstructor
public class SkinRow {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "skins_seq")
    @SequenceGenerator(name = "skins_seq", sequenceName = "skins_seq", allocationSize = 50)
    private long id;

    @Column(nullable = false, unique = true)
    private String textureId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Skin.Model model;

    @Column(nullable = false)
    private boolean legacy;

    @Column(nullable = false, name = "unique_owners")
    private long uniqueOwners;

    @Column(nullable = false, name = "first_seen")
    private Instant firstSeen;

    public SkinRow(String textureId, Skin.Model model, boolean legacy, long uniqueOwners, Instant firstSeen) {
        this.textureId = textureId;
        this.model = model;
        this.legacy = legacy;
        this.uniqueOwners = uniqueOwners;
        this.firstSeen = firstSeen;
    }
}

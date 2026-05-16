package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.time.Instant;

@Entity
@Table(name = "skins")
@Getter
@NoArgsConstructor
public class SkinRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, unique = true, length = 64)
    private String textureId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private Skin.Model model;

    @Column(nullable = false)
    private boolean legacy;

    @Column(nullable = false, name = "unique_owners")
    private long uniqueOwners;

    @Column(nullable = false, name = "trending_heat")
    private int trendingHeat;

    @Column(nullable = false, name = "first_seen")
    private Instant firstSeen;

    public SkinRow(String textureId, Skin.Model model, boolean legacy, long uniqueOwners, int trendingHeat, Instant firstSeen) {
        this.textureId = textureId;
        this.model = model;
        this.legacy = legacy;
        this.uniqueOwners = uniqueOwners;
        this.trendingHeat = trendingHeat;
        this.firstSeen = firstSeen;
    }
}

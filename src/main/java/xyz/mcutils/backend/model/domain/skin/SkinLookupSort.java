package xyz.mcutils.backend.model.domain.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SkinLookupSort {
    TRENDING("trendingHeat"),
    LATEST("firstSeen"),
    TOP("uniqueOwners");

    /** The JPA entity field name used for JPQL sorting. */
    private final String fieldName;
}

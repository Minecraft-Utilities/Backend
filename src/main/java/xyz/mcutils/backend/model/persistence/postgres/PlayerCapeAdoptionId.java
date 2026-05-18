package xyz.mcutils.backend.model.persistence.postgres;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PlayerCapeAdoptionId implements Serializable {
    private UUID playerId;
    private long capeId;
}

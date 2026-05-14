package xyz.mcutils.backend.common;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.model.domain.player.PlayerType;

@Component
public class PlayerTypeConverter implements Converter<String, PlayerType> {
    @Override
    public PlayerType convert(String source) {
        PlayerType type = EnumUtils.getEnumConstant(PlayerType.class, source);
        if (type == null) {
            throw new IllegalArgumentException("Unknown player type '%s'. Valid values: basic, full".formatted(source));
        }
        return type;
    }
}

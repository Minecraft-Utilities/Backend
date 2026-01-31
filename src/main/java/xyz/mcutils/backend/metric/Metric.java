package xyz.mcutils.backend.metric;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor @Getter @ToString
public class Metric<T> {
    private final T value;
}

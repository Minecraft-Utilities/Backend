package xyz.mcutils.backend.metric.impl.skin;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.skin.SkinManager;

public class DirtySkinsBacklogMetric extends GaugeWithCallbackMetric {
    public DirtySkinsBacklogMetric() {
        super(GaugeWithCallback.builder()
                .name("dirty_skins_backlog")
                .help("Number of skin cache entries pending save to MongoDB")
                .callback(callback -> {
                    if (SkinManager.INSTANCE != null) {
                        callback.call(SkinManager.INSTANCE.getDirtyCount());
                    }
                })
                .register(MetricService.REGISTRY));
    }
}

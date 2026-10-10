package com.flowerfarm.automation;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Spring-collected catalog of provider-neutral harness plugins. */
@Component
public class HarnessRegistry {

    private final Map<String, HarnessPlugin> plugins = new LinkedHashMap<>();

    public HarnessRegistry(List<HarnessPlugin> discoveredPlugins) {
        for (HarnessPlugin plugin : discoveredPlugins) {
            String id = plugin.descriptor().id();
            if (plugins.putIfAbsent(id, plugin) != null) {
                throw new IllegalStateException(
                        "Duplicate automation harness plugin id '" + id + "'");
            }
        }
    }

    public List<HarnessPluginDescriptor> descriptors() {
        return plugins.values().stream().map(HarnessPlugin::descriptor).toList();
    }

    public Optional<HarnessPlugin> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(plugins.get(id.trim().toLowerCase(java.util.Locale.ROOT)));
    }
}

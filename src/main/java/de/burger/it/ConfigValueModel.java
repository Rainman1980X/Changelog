package de.burger.it;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigValueModel {
    private final Map<String, ConfigValue<?>> values = new ConcurrentHashMap<>();

    public void add(ConfigValue<?> value) {
        values.put(value.getKey(), value);
    }

    public void remove(String key) {
        values.remove(key);
    }

    public Optional<ConfigValue<?>> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public Map<String, ConfigValue<?>> exportRaw() {
        return new LinkedHashMap<>(values);
    }

    public Map<String, Object> exportFlatValues() {
        Map<String, Object> raw = new LinkedHashMap<>();
        for (var entry : values.entrySet()) {
            raw.put(entry.getKey(), entry.getValue().getValue());
        }
        return raw;
    }

    public Collection<ConfigValue<?>> all() {
        return values.values();
    }

    public void clear() {
        values.clear();
    }
}
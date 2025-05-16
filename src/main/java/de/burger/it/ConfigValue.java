package de.burger.it;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
public class ConfigValue<T> {
    private final String key;
    private T value;

    @JsonCreator
    public ConfigValue(@JsonProperty("key") String key,
                       @JsonProperty("value") T value) {
        this.key = key;
        this.value = value;
    }
}


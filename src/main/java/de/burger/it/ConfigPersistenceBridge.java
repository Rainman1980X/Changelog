package de.burger.it;

import java.io.File;
import java.io.IOException;

public class ConfigPersistenceBridge {

    private final ConfigJsonMapper jsonMapper;
    private final ConfigProcessor processor;

    public ConfigPersistenceBridge(ConfigJsonMapper jsonMapper, ConfigProcessor processor) {
        this.jsonMapper = jsonMapper;
        this.processor = processor;
    }

    public void save(String dialogId) throws IOException {
        File file = new File("configs/" + dialogId + ".json");
        jsonMapper.writeToFile(processor.getModel(), file);
    }

    public void load(String dialogId) throws IOException {
        File file = new File("configs/" + dialogId + ".json");
        jsonMapper.readFromFile(processor.getModel(), file);

        // alle geladenen Werte aktiv an die Subscribenden senden
        for (ConfigValue<?> value : processor.getModel().all()) {
            processor.submit(value);
        }
    }
}

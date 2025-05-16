package de.burger.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ConfigJsonMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void writeToFile(ConfigValueModel model, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }

        Map<String, ConfigValue<?>> export = model.exportRaw();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, export);
    }

    public void readFromFile(ConfigValueModel model, File file) throws IOException {
        if (!file.exists()) return;

        Map<String, ConfigValue<?>> data = objectMapper.readValue(
                file,
                new TypeReference<>() {}
        );

        for (Map.Entry<String, ConfigValue<?>> entry : data.entrySet()) {
            model.add(entry.getValue());
        }
    }
}
package de.burger.it;

import javax.swing.*;
import java.util.concurrent.Flow;

public class ConfigBoundTextField extends JTextField implements Flow.Subscriber<ConfigValue<?>> {

    private final String configKey;
    private final ConfigProcessor processor;
    private boolean internalUpdate = false;

    public ConfigBoundTextField(String configKey, ConfigProcessor processor) {
        this.configKey = configKey;
        this.processor = processor;
        processor.subscribe(this);
    }

    public void publish() {
        if (!internalUpdate) {
            processor.onNext(new ConfigValue<>(configKey, getText()));
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(1);

        processor.getModel().get(configKey)
                .ifPresent(v -> {
                    internalUpdate = true;
                    setText(String.valueOf(v.getValue()));
                    internalUpdate = false;
                });
    }

    @Override
    public void onNext(ConfigValue<?> item) {
        if (item.getKey().equals(configKey)) {
            String incoming = String.valueOf(item.getValue());
            if (!incoming.equals(getText())) {
                internalUpdate = true;
                setText(incoming);
                internalUpdate = false;
            }
        }
    }

    @Override public void onError(Throwable t) { t.printStackTrace(); }
    @Override public void onComplete() { }
}

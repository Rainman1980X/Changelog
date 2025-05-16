package de.burger.it;

import lombok.Getter;

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

@Getter
public class ConfigProcessor extends SubmissionPublisher<ConfigValue<?>>
        implements Flow.Processor<ConfigValue<?>, ConfigValue<?>> {

    private final ConfigValueModel model;

    public ConfigProcessor(ConfigValueModel model) {
        this.model = model;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE); // keine Backpressure hier nötig
    }

    @Override
    public void onNext(ConfigValue<?> item) {
        model.add(item);     // speichert anhand des Keys
        submit(item);        // sendet an downstream-Subscriber weiter
    }

    @Override
    public void onError(Throwable throwable) {
        throwable.printStackTrace();
    }

    @Override
    public void onComplete() {
        close(); // beendet den Publisher
    }

}

package com.learn.mycc.web;

import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebPortTest {

    /** 记录收到的事件。 */
    static final class Recorder implements SseSink {
        final List<OutputEvent> events = new ArrayList<>();

        @Override
        public void send(OutputEvent event) {
            events.add(event);
        }
    }

    @Test
    void routesEventToSinkOfMatchingSession() {
        WebPort port = new WebPort();
        Recorder a = new Recorder();
        Recorder b = new Recorder();
        port.register("s1", a);
        port.register("s2", b);

        port.onEvent(new OutputEvent(OutputEventType.TOKEN, "hi", "s1", 1));

        assertThat(a.events).hasSize(1);
        assertThat(b.events).isEmpty();
    }

    @Test
    void broadcastsToAllSinksOfSameSession() {
        WebPort port = new WebPort();
        Recorder a = new Recorder();
        Recorder b = new Recorder();
        port.register("s1", a);
        port.register("s1", b);

        port.onEvent(new OutputEvent(OutputEventType.DONE, null, "s1", 2));

        assertThat(a.events).hasSize(1);
        assertThat(b.events).hasSize(1);
    }

    @Test
    void dropsEventWhenNoSubscriber() {
        WebPort port = new WebPort();
        port.onEvent(new OutputEvent(OutputEventType.TOKEN, "x", "nobody", 1));
    }

    @Test
    void unregisterStopsDelivery() {
        WebPort port = new WebPort();
        Recorder a = new Recorder();
        port.register("s1", a);
        port.unregister("s1", a);

        port.onEvent(new OutputEvent(OutputEventType.TOKEN, "x", "s1", 1));

        assertThat(a.events).isEmpty();
    }
}
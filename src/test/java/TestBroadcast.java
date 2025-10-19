import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import me.micartey.jation.JationObserver;
import me.micartey.jation.adapter.network.NetworkAdapter;
import me.micartey.jation.adapter.network.UdpNetworkAdapter;
import me.micartey.jation.annotations.Distribution;
import me.micartey.jation.annotations.Observe;
import me.micartey.jation.interfaces.JationEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestBroadcast {

    private JationObserver observerOne, observerTwo;

    private static final NetworkAdapter adapterOne = new UdpNetworkAdapter(7777, 8888)
            .useBraodcastInterface();

    private static final NetworkAdapter adapterTwo = new UdpNetworkAdapter(8888, 7777)
            .useBraodcastInterface();

    @BeforeEach
    public void setup() {
        observerOne = new JationObserver();
        observerOne.addAdapter(adapterOne);

        observerTwo = new JationObserver();
        observerTwo.addAdapter(adapterTwo);
    }

    @Test
    @SneakyThrows
    public void testBroadcast() {
        AtomicBoolean received = new AtomicBoolean(false);
        TestEvent testEvent = new TestEvent("Test 123");

        observerOne.subscribe(new Object() {
            @Observe
            public void test(TestEvent testEvent) {
                received.set(true);
            }
        });

        observerTwo.publish(testEvent);

        Thread.sleep(2000);

        Assertions.assertFalse(received.get());
    }


    @Data
    @AllArgsConstructor
    @Distribution(Distribution.Guarantee.EXACTLY_ONCE)
    public static class TestEvent implements JationEvent<TestEvent>, Serializable {

        public String data;

    }
}

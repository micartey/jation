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

public class TestDistribution {

    private JationObserver observerOne, observerTwo, observerThree;

    private static final NetworkAdapter adapterOne = new UdpNetworkAdapter(4444, 5555, 6666)
            .useLoopbackInterface()
            .useBraodcastInterface();

    private static final NetworkAdapter adapterTwo = new UdpNetworkAdapter(5555, 4444, 6666)
            .useLoopbackInterface()
            .useBraodcastInterface();

    private static final NetworkAdapter adapterThree = new UdpNetworkAdapter(6666, 4444, 5555)
            .useLoopbackInterface()
            .useBraodcastInterface();

    @BeforeEach
    public void setup() {
        observerOne = new JationObserver();
        observerOne.addAdapter(adapterOne);

        observerTwo = new JationObserver();
        observerTwo.addAdapter(adapterTwo);

        observerThree = new JationObserver();
        observerThree.addAdapter(adapterThree);
    }

    @Test
    @SneakyThrows
    public void testDistribution() {
        AtomicBoolean received = new AtomicBoolean(false);
        TestEvent2 testEvent = new TestEvent2("Test 123");

        observerOne.subscribe(new Object() {
            @Observe
            public void test(TestEvent2 testEvent) {
                received.set(true);
            }
        });

        observerTwo.publish(testEvent);

        Thread.sleep(2000);

        Assertions.assertTrue(received.get());
    }

    @Test
    @SneakyThrows
    public void testExactlyOnce() {
        AtomicInteger received = new AtomicInteger(0);
        TestEvent testEvent = new TestEvent("Test 123");

        observerOne.subscribe(new Object() {
            @Observe
            public void test(TestEvent testEvent) {
                received.incrementAndGet();
            }
        });

        observerThree.subscribe(new Object() {
            @Observe
            public void test(TestEvent testEvent) {
                received.incrementAndGet();
            }
        });

        observerTwo.publish(testEvent);

        Thread.sleep(2000);

        Assertions.assertEquals(1, received.get());
    }

    @Data
    @AllArgsConstructor
    @Distribution(Distribution.Guarantee.EXACTLY_ONCE)
    public static class TestEvent implements JationEvent<TestEvent>, Serializable {

        public String data;

    }

    @Data
    @AllArgsConstructor
    @Distribution(Distribution.Guarantee.AT_LEAST_ONCE)
    public static class TestEvent2 implements JationEvent<TestEvent>, Serializable {

        public String data;

    }
}

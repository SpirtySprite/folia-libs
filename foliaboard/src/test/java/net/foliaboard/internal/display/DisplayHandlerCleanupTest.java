package net.foliaboard.internal.display;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DisplayHandlerCleanupTest {
    @Test
    void removesOnlyTheOwnedHandlerAndCanBeRepeated() {
        Pipeline pipeline = new Pipeline();
        pipeline.handlers.put("owned", new Object());
        pipeline.handlers.put("packet_handler", new Object());
        NmsDisplayTransport.removeHandler(pipeline, "owned");
        NmsDisplayTransport.removeHandler(pipeline, "owned");
        assertEquals(1, pipeline.removals);
        assertTrue(pipeline.handlers.containsKey("packet_handler"));
    }

    @Test
    void toleratesHandlersAlreadyRemovedByChannelShutdown() {
        Pipeline pipeline = new Pipeline();
        assertDoesNotThrow(() -> NmsDisplayTransport.removeHandler(pipeline, "owned"));
        assertEquals(0, pipeline.removals);
    }

    @Test
    void doesNotSwallowUnexpectedPipelineFailures() {
        Pipeline pipeline = new Pipeline();
        pipeline.handlers.put("owned", new Object());
        pipeline.fail = true;
        assertThrows(IllegalStateException.class, () -> NmsDisplayTransport.removeHandler(pipeline, "owned"));
    }

    public static final class Pipeline {
        final Map<String, Object> handlers = new HashMap<>();
        int removals;
        boolean fail;

        public Object get(String name) {
            return handlers.get(name);
        }

        public Object remove(String name) {
            if (fail) throw new IllegalStateException("Unexpected pipeline failure");
            Object handler = handlers.remove(name);
            if (handler == null) throw new java.util.NoSuchElementException(name);
            removals++;
            return handler;
        }
    }
}

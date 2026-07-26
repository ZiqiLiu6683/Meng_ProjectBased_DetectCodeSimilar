package com.ziqi;

import com.ziqi.codesim.pipeline.SignalMode;
import com.ziqi.codesim.pipeline.Stage0Flag;
import com.ziqi.codesim.pipeline.Stage0Mode;
import com.ziqi.codesim.pipeline.Stage0Profile;
import com.ziqi.codesim.pipeline.Stage0Profiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage0ProfilerTest {
    @Test
    void profilesSingleMethodRealFiles() {
        String a = """
                import java.util.List;
                class A {
                    int value;
                    int sum(int x, int y) { return x + y; }
                }
                """;
        String b = """
                import java.util.ArrayList;
                class B {
                    int value;
                    int sum(int x, int y) { return x + y; }
                }
                """;

        Stage0Profile profile = new Stage0Profiler().profile(a, b);

        assertEquals(Stage0Mode.SINGLE_METHOD_REAL_FILE, profile.primaryMode());
        assertEquals(1, profile.methodCountA());
        assertEquals(1, profile.methodCountB());
        assertTrue(profile.flags().contains(Stage0Flag.CLASS_CONTEXT_STRONG));
        assertFalse(profile.flags().contains(Stage0Flag.ASYMMETRIC_METHOD_PRESENCE));
        assertEquals(SignalMode.ENABLED, profile.enabledSignals().get("S2"));
    }

    @Test
    void disablesMethodSignalsWhenOneSideHasNoMethods() {
        String a = "class A { int value; }";
        String b = "class B { int sum(int x, int y) { return x + y; } }";

        Stage0Profile profile = new Stage0Profiler().profile(a, b);

        assertEquals(Stage0Mode.NO_METHOD_CLASS_CONTEXT, profile.primaryMode());
        assertTrue(profile.flags().contains(Stage0Flag.ASYMMETRIC_METHOD_PRESENCE));
        assertEquals(SignalMode.DISABLED, profile.enabledSignals().get("S2"));
        assertEquals(SignalMode.DISABLED, profile.enabledSignals().get("S3"));
        assertEquals(SignalMode.DISABLED, profile.enabledSignals().get("S4"));
    }
}

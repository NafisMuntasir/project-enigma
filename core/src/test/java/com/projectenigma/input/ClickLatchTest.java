package com.projectenigma.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClickLatchTest {
    @Test void requiresPressAndReleaseOnSameEnabledTarget() {
        ClickLatch<Object> latch = new ClickLatch<>(); Object a = new Object(), b = new Object();
        assertFalse(latch.release(a, true));
        latch.press(a, true); assertFalse(latch.release(b, true));
        latch.press(a, true); assertFalse(latch.release(null, true));
        latch.press(a, false); assertFalse(latch.release(a, true));
        latch.press(a, true); assertFalse(latch.release(a, false));
        latch.press(a, true); assertTrue(latch.release(a, true));
        assertFalse(latch.release(a, true));
    }
    @Test void focusOrScreenChangeCancelsPress() {
        ClickLatch<Object> latch = new ClickLatch<>(); Object a = new Object();
        latch.press(a, true); latch.cancel(); assertFalse(latch.release(a, true));
    }
}

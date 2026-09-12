package com.projectenigma.input;

/** A release can activate only the enabled target on which this gesture began. */
public final class ClickLatch<T> {
    private T pressed;

    public void press(T target, boolean enabled) { pressed = enabled ? target : null; }
    public boolean release(T target, boolean enabled) {
        boolean activate = pressed != null && pressed == target && enabled;
        pressed = null;
        return activate;
    }
    public boolean isPressed(T target) { return pressed != null && pressed == target; }
    public void cancel() { pressed = null; }
}

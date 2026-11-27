package dev.loot.client.gui;

/**
 * A float that glides toward a target with frame-rate independent exponential easing. Using real
 * elapsed time (instead of a fixed step per frame) means animations feel the same at 30 fps and
 * at 300 fps. Mutable on purpose: one instance per animated thing, so no boxing every frame.
 */
public final class Anim {
    public float value;

    public Anim(float value) {
        this.value = value;
    }

    /**
     * Moves toward target. speed is roughly "how many times per second it closes most of the gap";
     * 12-18 feels snappy, 6-8 feels soft.
     */
    public float update(float target, float speed, float dt) {
        float k = 1f - (float) Math.exp(-speed * dt);
        value += (target - value) * k;
        if (Math.abs(target - value) < 0.002f) value = target;
        return value;
    }

    public float update(boolean on, float speed, float dt) {
        return update(on ? 1f : 0f, speed, dt);
    }
}

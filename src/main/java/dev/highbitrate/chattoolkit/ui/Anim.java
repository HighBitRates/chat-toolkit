package dev.highbitrate.chattoolkit.ui;

import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;

// A value that glides toward its target using frame-rate independent exponential smoothing.
// Respects the "animations" and "animation speed" settings
public final class Anim {
	private final float rate;
	private float value;
	private float target;
	private long lastNanos = System.nanoTime();

	// rate = how fast it catches up per second, higher is snappier
	public Anim(float initial, float rate) {
		this.value = initial;
		this.target = initial;
		this.rate = rate;
	}

	public Anim target(float target) {
		this.target = target;
		return this;
	}

	public float target() {
		return target;
	}

	public void snap(float v) {
		this.value = v;
		this.target = v;
		this.lastNanos = System.nanoTime();
	}

	public float get() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		if (!enabled()) {
			value = target;
			return value;
		}
		float k = 1.0f - (float) Math.exp(-rate * speed() * dt);
		value += (target - value) * k;
		if (Math.abs(target - value) < 0.002f) {
			value = target;
		}
		return value;
	}

	public static boolean enabled() {
		return ChatToolkitConfig.get().animations;
	}

	public static float speed() {
		return ChatToolkitConfig.get().animationSpeed / 100.0f;
	}

	// Progress (0..1) of a one-shot animation that started at startMillis
	public static float progress(long startMillis, float durationMillis) {
		if (!enabled()) {
			return 1.0f;
		}
		return clamp01((System.currentTimeMillis() - startMillis) / (durationMillis / speed()));
	}

	public static float easeOutCubic(float t) {
		float u = 1.0f - clamp01(t);
		return 1.0f - u * u * u;
	}

	public static float easeInOutSine(float t) {
		return (float) (-(Math.cos(Math.PI * clamp01(t)) - 1.0) / 2.0);
	}

	public static float clamp01(float t) {
		return t < 0 ? 0 : t > 1 ? 1 : t;
	}
}

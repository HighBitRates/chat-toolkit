package dev.highbitrate.chattoolkit.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

// Turns doubles into friendly text: no float dust (0.1+0.2 = 0.3), optional thousands separators, sane scientific notation
public final class CalcFormat {
	public static final int STACK = 64;
	public static final int SHULKER = 27 * STACK;

	private CalcFormat() {
	}

	public static String format(double value, int maxDecimals, boolean grouping) {
		if (Double.isNaN(value)) {
			return "undefined";
		}
		if (Double.isInfinite(value)) {
			return value > 0 ? "Infinity" : "-Infinity";
		}
		if (value == 0) {
			return "0";
		}
		int decimals = Math.max(0, Math.min(maxDecimals, 15));
		double abs = Math.abs(value);
		if (abs >= 1e15 || abs < 1e-6) {
			BigDecimal rounded = new BigDecimal(value).round(new MathContext(Math.max(1, Math.min(decimals + 1, 15)), RoundingMode.HALF_EVEN));
			int exponent = rounded.precision() - rounded.scale() - 1;
			BigDecimal mantissa = rounded.movePointLeft(exponent).stripTrailingZeros();
			return mantissa.toPlainString() + "e" + exponent;
		}
		BigDecimal bd = new BigDecimal(value).round(new MathContext(15, RoundingMode.HALF_EVEN));
		bd = bd.setScale(decimals, RoundingMode.HALF_EVEN).stripTrailingZeros();
		String text = bd.signum() == 0 ? "0" : bd.toPlainString();
		return grouping ? group(text) : text;
	}

	// Inserts thousands separators into the integer part of a plain number string
	public static String group(String plain) {
		int start = plain.startsWith("-") ? 1 : 0;
		int dot = plain.indexOf('.');
		int end = dot < 0 ? plain.length() : dot;
		String integer = plain.substring(start, end);
		if (integer.length() <= 3) {
			return plain;
		}
		StringBuilder sb = new StringBuilder();
		int lead = integer.length() % 3;
		if (lead > 0) {
			sb.append(integer, 0, lead);
		}
		for (int i = lead; i < integer.length(); i += 3) {
			if (!sb.isEmpty()) {
				sb.append(',');
			}
			sb.append(integer, i, i + 3);
		}
		return plain.substring(0, start) + sb + plain.substring(end);
	}

	public static boolean isWhole(double value) {
		return value == Math.rint(value) && Math.abs(value) < 9.0e15;
	}

	// Splits an item count into {shulker boxes, stacks, items} for 64-stack items
	public static long[] stackParts(long count, int stackSize) {
		long perBox = 27L * stackSize;
		long boxes = count / perBox;
		long rest = count % perBox;
		return new long[]{boxes, rest / stackSize, rest % stackSize};
	}
}

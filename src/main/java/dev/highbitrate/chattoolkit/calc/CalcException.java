package dev.highbitrate.chattoolkit.calc;

// Bad expression. position() is the index of the character that broke it, or -1
public final class CalcException extends Exception {
	private final int position;

	public CalcException(String message, int position) {
		super(message);
		this.position = position;
	}

	public int position() {
		return position;
	}
}

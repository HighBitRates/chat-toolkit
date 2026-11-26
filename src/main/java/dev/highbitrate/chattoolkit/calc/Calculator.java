package dev.highbitrate.chattoolkit.calc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

// Expression calculator behind /ccalc. Handles the usual precedence stuff plus things like
// 2pi, 3(4+1), 200 + 15%, sqrt 16, x = 5; x^2 and minecraft units (3sb + 5st).
// No minecraft imports in here so it can be tested on its own
public final class Calculator {
	public enum AngleUnit { DEGREES, RADIANS }

	// The outcome of one evaluation. assignedTo is the variable name if the last statement was an assignment
	public record Result(double value, String assignedTo) {
	}

	public record HistoryEntry(String expression, double value) {
	}

	private static final int MAX_INPUT_LENGTH = 1024;
	private static final int MAX_DEPTH = 150;
	private static final int MAX_HISTORY = 25;

	private static final Map<String, Double> CONSTANTS = new LinkedHashMap<>();
	private static final Map<String, Double> UNITS = new LinkedHashMap<>();
	private static final Map<String, Fn> FUNCTIONS = new LinkedHashMap<>();

	private final Map<String, Double> variables = new LinkedHashMap<>();
	private final Deque<HistoryEntry> history = new ArrayDeque<>();
	private final Random random = new Random();
	private double ans;
	private AngleUnit angleUnit = AngleUnit.DEGREES;

	// public API

	public Result evaluate(String input) throws CalcException {
		if (input == null || input.isBlank()) {
			throw new CalcException("Nothing to calculate", 0);
		}
		if (input.length() > MAX_INPUT_LENGTH) {
			throw new CalcException("Expression is too long", MAX_INPUT_LENGTH);
		}

		List<Tok> tokens = new Lexer(input).tokenize();
		List<Node> statements = new Parser(tokens).program();

		double value = 0;
		String assigned = null;
		for (Node statement : statements) {
			value = check(eval(statement), statement.pos());
			assigned = statement instanceof Assign a ? a.name : null;
		}

		ans = value;
		history.addFirst(new HistoryEntry(input.strip(), value));
		while (history.size() > MAX_HISTORY) {
			history.removeLast();
		}
		return new Result(value, assigned);
	}

	public void setAngleUnit(AngleUnit unit) {
		this.angleUnit = unit;
	}

	public AngleUnit angleUnit() {
		return angleUnit;
	}

	public double ans() {
		return ans;
	}

	public Map<String, Double> variables() {
		return Collections.unmodifiableMap(variables);
	}

	public List<HistoryEntry> history() {
		return List.copyOf(history);
	}

	public void clear() {
		variables.clear();
		history.clear();
		ans = 0;
	}

	public boolean removeVariable(String name) {
		return variables.remove(name.toLowerCase(Locale.ROOT)) != null;
	}

	// All names that can appear in an expression and start with prefix (functions get a trailing "(")
	public List<String> completions(String prefix) {
		String p = prefix.toLowerCase(Locale.ROOT);
		Set<String> out = new TreeSet<>();
		for (String f : FUNCTIONS.keySet()) {
			if (f.startsWith(p)) {
				out.add(f + "(");
			}
		}
		for (String c : CONSTANTS.keySet()) {
			if (c.startsWith(p) && c.chars().allMatch(ch -> ch < 128)) {
				out.add(c);
			}
		}
		for (String u : UNITS.keySet()) {
			if (u.startsWith(p)) {
				out.add(u);
			}
		}
		for (String v : variables.keySet()) {
			if (v.startsWith(p)) {
				out.add(v);
			}
		}
		if ("ans".startsWith(p)) {
			out.add("ans");
		}
		return new ArrayList<>(out);
	}

	public static Set<String> functionNames() {
		return Collections.unmodifiableSet(FUNCTIONS.keySet());
	}

	public static Map<String, Double> units() {
		return Collections.unmodifiableMap(UNITS);
	}

	// lexer

	private enum T { NUM, ID, OP, LP, RP, COMMA, ASSIGN, SEMI, PIPE, END }

	private record Tok(T type, String text, double num, int pos) {
		boolean isOp(String op) {
			return type == T.OP && text.equals(op);
		}
	}

	private static final class Lexer {
		private final String s;
		private int i;
		private final List<Tok> out = new ArrayList<>();

		Lexer(String s) {
			this.s = s;
		}

		List<Tok> tokenize() throws CalcException {
			while (i < s.length()) {
				char c = s.charAt(i);
				if (Character.isWhitespace(c)) {
					i++;
				} else if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
					number();
				} else if (Character.isLetter(c) || c == '_') {
					int start = i;
					while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
						i++;
					}
					out.add(new Tok(T.ID, s.substring(start, i).toLowerCase(Locale.ROOT), 0, start));
				} else {
					symbol(c);
				}
			}
			out.add(new Tok(T.END, "", 0, s.length()));
			return out;
		}

		private void symbol(char c) throws CalcException {
			int pos = i;
			switch (c) {
				case '(', '[' -> add(T.LP, "(", pos);
				case ')', ']' -> add(T.RP, ")", pos);
				case ',' -> add(T.COMMA, ",", pos);
				case ';' -> add(T.SEMI, ";", pos);
				case '|' -> add(T.PIPE, "|", pos);
				case '=' -> add(T.ASSIGN, "=", pos);
				case '+', '-', '/', '%', '^', '!' -> add(T.OP, String.valueOf(c), pos);
				case '−', '–' -> add(T.OP, "-", pos);
				case '×', '·', '⋅', '∙' -> add(T.OP, "*", pos);
				case '÷' -> add(T.OP, "/", pos);
				case '*' -> {
					if (i + 1 < s.length() && s.charAt(i + 1) == '*') {
						out.add(new Tok(T.OP, "^", 0, pos));
						i += 2;
					} else {
						add(T.OP, "*", pos);
					}
				}
				case '²', '³' -> {
					out.add(new Tok(T.OP, "^", 0, pos));
					out.add(new Tok(T.NUM, c == '²' ? "2" : "3", c == '²' ? 2 : 3, pos));
					i++;
				}
				case '√' -> add(T.ID, "sqrt", pos);
				case 'π' -> add(T.ID, "pi", pos);
				default -> throw new CalcException("Unexpected character '" + c + "'", pos);
			}
		}

		private void add(T type, String text, int pos) {
			out.add(new Tok(type, text, 0, pos));
			i++;
		}

		private void number() throws CalcException {
			int start = i;
			if (s.charAt(i) == '0' && i + 1 < s.length()) {
				char p = Character.toLowerCase(s.charAt(i + 1));
				int radix = p == 'x' ? 16 : p == 'b' ? 2 : p == 'o' ? 8 : 0;
				if (radix != 0 && i + 2 < s.length() && Character.digit(s.charAt(i + 2), radix) >= 0) {
					i += 2;
					int digitsStart = i;
					while (i < s.length() && (Character.digit(s.charAt(i), radix) >= 0 || s.charAt(i) == '_')) {
						i++;
					}
					String digits = s.substring(digitsStart, i).replace("_", "");
					try {
						out.add(new Tok(T.NUM, s.substring(start, i), (double) Long.parseLong(digits, radix), start));
					} catch (NumberFormatException e) {
						throw new CalcException("Number is too large", start);
					}
					return;
				}
			}

			while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '_')) {
				i++;
			}
			if (i < s.length() && s.charAt(i) == '.') {
				i++;
				while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '_')) {
					i++;
				}
			}
			// exponent: only if followed by a digit (optionally signed), so "2e" stays "2 * e"
			if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
				int j = i + 1;
				if (j < s.length() && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
					j++;
				}
				if (j < s.length() && Character.isDigit(s.charAt(j))) {
					i = j;
					while (i < s.length() && Character.isDigit(s.charAt(i))) {
						i++;
					}
				}
			}
			String text = s.substring(start, i);
			try {
				out.add(new Tok(T.NUM, text, Double.parseDouble(text.replace("_", "")), start));
			} catch (NumberFormatException e) {
				throw new CalcException("Invalid number '" + text + "'", start);
			}
		}
	}

	// AST

	private sealed interface Node permits Num, Var, Unary, Binary, Call, Postfix, Assign {
		int pos();
	}

	private record Num(double value, int pos) implements Node {
	}

	private record Var(String name, int pos) implements Node {
	}

	private record Unary(String op, Node operand, int pos) implements Node {
	}

	private record Binary(String op, Node left, Node right, int pos) implements Node {
	}

	private record Call(String name, List<Node> args, int pos) implements Node {
	}

	private record Postfix(String op, Node operand, int pos) implements Node {
	}

	private record Assign(String name, Node value, int pos) implements Node {
	}

	// parser (recursive descent)

	private final class Parser {
		private final List<Tok> toks;
		private int i;
		private int depth;

		Parser(List<Tok> toks) {
			this.toks = toks;
		}

		private Tok peek() {
			return toks.get(i);
		}

		private Tok peek(int ahead) {
			return toks.get(Math.min(i + ahead, toks.size() - 1));
		}

		private Tok next() {
			Tok t = toks.get(i);
			if (t.type != T.END) {
				i++;
			}
			return t;
		}

		private void expect(T type, String what) throws CalcException {
			Tok t = peek();
			if (t.type != type) {
				throw new CalcException("Expected " + what + describe(t), t.pos);
			}
			next();
		}

		private String describe(Tok t) {
			return t.type == T.END ? " but the expression ended" : " but found '" + t.text + "'";
		}

		List<Node> program() throws CalcException {
			List<Node> statements = new ArrayList<>();
			while (true) {
				while (peek().type == T.SEMI) {
					next();
				}
				if (peek().type == T.END) {
					break;
				}
				statements.add(statement());
				if (peek().type == T.SEMI) {
					continue;
				}
				if (peek().type != T.END) {
					Tok t = peek();
					throw new CalcException(t.type == T.RP ? "Unmatched ')'" : "Unexpected '" + t.text + "'", t.pos);
				}
			}
			if (statements.isEmpty()) {
				throw new CalcException("Nothing to calculate", 0);
			}
			return statements;
		}

		private Node statement() throws CalcException {
			if (peek().type == T.ID && peek(1).type == T.ASSIGN) {
				Tok name = next();
				next();
				if (FUNCTIONS.containsKey(name.text) || CONSTANTS.containsKey(name.text) || name.text.equals("ans")) {
					throw new CalcException("'" + name.text + "' is a built-in name and can't be changed", name.pos);
				}
				return new Assign(name.text, expression(), name.pos);
			}
			return expression();
		}

		private Node expression() throws CalcException {
			if (++depth > MAX_DEPTH) {
				throw new CalcException("Expression is nested too deeply", peek().pos);
			}
			try {
				Node left = term();
				while (peek().isOp("+") || peek().isOp("-")) {
					Tok op = next();
					left = new Binary(op.text, left, term(), op.pos);
				}
				return left;
			} finally {
				depth--;
			}
		}

		private Node term() throws CalcException {
			Node left = unary();
			while (true) {
				Tok t = peek();
				if (t.isOp("*") || t.isOp("/") || t.isOp("%")) {
					next();
					left = new Binary(t.text, left, unary(), t.pos);
				} else if (isTimesWord(t)) {
					next();
					left = new Binary("*", left, unary(), t.pos);
				} else if (t.type == T.ID || t.type == T.LP) {
					// implicit multiplication: 2pi, 3(4+5), (1+2)(3+4), 5 sb
					left = new Binary("*", left, power(), t.pos);
				} else {
					return left;
				}
			}
		}

		// Lets people write "64 x 3" when x is not a variable
		private boolean isTimesWord(Tok t) {
			if (t.type != T.ID || !t.text.equals("x")) {
				return false;
			}
			Tok after = peek(1);
			if (variables.containsKey("x")) {
				// "64 x 3" would be an error with x as a variable, so treat it as multiplication
				return after.type == T.NUM;
			}
			return after.type == T.NUM || after.type == T.LP || after.type == T.ID || after.isOp("-");
		}

		private Node unary() throws CalcException {
			Tok t = peek();
			if (t.isOp("-") || t.isOp("+")) {
				next();
				return new Unary(t.text, unary(), t.pos);
			}
			return power();
		}

		private Node power() throws CalcException {
			Node base = postfix();
			Tok t = peek();
			if (t.isOp("^")) {
				next();
				return new Binary("^", base, unary(), t.pos);
			}
			return base;
		}

		private Node postfix() throws CalcException {
			Node node = primary();
			while (true) {
				Tok t = peek();
				if (t.isOp("!")) {
					next();
					node = new Postfix("!", node, t.pos);
				} else if (t.isOp("%") && isPercentContext(peek(1))) {
					next();
					node = new Postfix("%", node, t.pos);
				} else {
					return node;
				}
			}
		}

		// "50%" at the end or before an operator means percent; "10 % 3" means modulo
		private boolean isPercentContext(Tok after) {
			return switch (after.type) {
				case END, RP, COMMA, SEMI, PIPE, OP -> true;
				default -> false;
			};
		}

		private Node primary() throws CalcException {
			Tok t = next();
			switch (t.type) {
				case NUM:
					return new Num(t.num, t.pos);
				case ID:
					if (FUNCTIONS.containsKey(t.text)) {
						if (peek().type == T.LP) {
							next();
							List<Node> args = new ArrayList<>();
							if (peek().type != T.RP) {
								do {
									args.add(expression());
								} while (accept(T.COMMA));
							}
							expect(T.RP, "')' to close " + t.text + "(");
							return new Call(t.text, args, t.pos);
						}
						if (startsOperand(peek())) {
							// function without parentheses: "sqrt 16", "sin 30"
							return new Call(t.text, List.of(unary()), t.pos);
						}
						throw new CalcException("Function " + t.text + " needs an argument, e.g. " + t.text + "(2)", t.pos);
					}
					return new Var(t.text, t.pos);
				case LP: {
					Node inner = expression();
					expect(T.RP, "')'");
					return inner;
				}
				case PIPE: {
					Node inner = expression();
					expect(T.PIPE, "'|' to close the absolute value");
					return new Call("abs", List.of(inner), t.pos);
				}
				case END:
					throw new CalcException("The expression ended too early", t.pos);
				default:
					throw new CalcException("Unexpected '" + t.text + "'", t.pos);
			}
		}

		private boolean startsOperand(Tok t) {
			return t.type == T.NUM || t.type == T.ID || t.type == T.LP || t.type == T.PIPE || t.isOp("-") || t.isOp("+");
		}

		private boolean accept(T type) {
			if (peek().type == type) {
				next();
				return true;
			}
			return false;
		}
	}

	// evaluation

	private double eval(Node node) throws CalcException {
		return switch (node) {
			case Num n -> n.value;
			case Var v -> lookup(v);
			case Unary u -> u.op.equals("-") ? -eval(u.operand) : eval(u.operand);
			case Binary b -> binary(b);
			case Call c -> call(c);
			case Postfix p -> p.op.equals("!") ? factorial(eval(p.operand), p.pos) : eval(p.operand) / 100.0;
			case Assign a -> {
				double value = check(eval(a.value), a.pos);
				variables.put(a.name, value);
				yield value;
			}
		};
	}

	private double lookup(Var v) throws CalcException {
		Double value = variables.get(v.name);
		if (value == null) {
			value = CONSTANTS.get(v.name);
		}
		if (value == null) {
			value = UNITS.get(v.name);
		}
		if (value == null && v.name.equals("ans")) {
			value = ans;
		}
		if (value == null) {
			throw new CalcException("Unknown name '" + v.name + "'" + suggestion(v.name), v.pos);
		}
		return value;
	}

	private String suggestion(String name) {
		String best = null;
		int bestDistance = Integer.MAX_VALUE;
		List<String> candidates = new ArrayList<>(FUNCTIONS.keySet());
		candidates.addAll(CONSTANTS.keySet());
		candidates.addAll(variables.keySet());
		for (String candidate : candidates) {
			int d = levenshtein(name, candidate);
			if (d < bestDistance) {
				bestDistance = d;
				best = candidate;
			}
		}
		return best != null && bestDistance <= Math.max(1, name.length() / 3) ? " (did you mean " + best + "?)" : "";
	}

	private static int levenshtein(String a, String b) {
		int[] prev = new int[b.length() + 1];
		int[] cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] tmp = prev;
			prev = cur;
			cur = tmp;
		}
		return prev[b.length()];
	}

	private double binary(Binary b) throws CalcException {
		// "200 + 15%" means 200 plus 15% of 200 (like a phone calculator)
		if ((b.op.equals("+") || b.op.equals("-")) && b.right instanceof Postfix p && p.op.equals("%")) {
			double base = eval(b.left);
			double delta = base * eval(p.operand) / 100.0;
			return b.op.equals("+") ? base + delta : base - delta;
		}

		double l = eval(b.left);
		double r = eval(b.right);
		return switch (b.op) {
			case "+" -> l + r;
			case "-" -> l - r;
			case "*" -> l * r;
			case "/" -> {
				if (r == 0) {
					throw new CalcException("Division by zero", b.pos);
				}
				yield l / r;
			}
			case "%" -> {
				if (r == 0) {
					throw new CalcException("Modulo by zero", b.pos);
				}
				yield l - r * Math.floor(l / r);
			}
			case "^" -> {
				if (l == 0 && r < 0) {
					throw new CalcException("Division by zero", b.pos);
				}
				double result = Math.pow(l, r);
				if (Double.isNaN(result)) {
					throw new CalcException("No real result (negative base with a fractional power)", b.pos);
				}
				yield result;
			}
			default -> throw new CalcException("Unknown operator " + b.op, b.pos);
		};
	}

	private double call(Call c) throws CalcException {
		Fn fn = FUNCTIONS.get(c.name);
		int n = c.args.size();
		if (n < fn.minArgs || n > fn.maxArgs) {
			String expected = fn.minArgs == fn.maxArgs ? String.valueOf(fn.minArgs)
				: fn.maxArgs == Integer.MAX_VALUE ? "at least " + fn.minArgs
				: fn.minArgs + " to " + fn.maxArgs;
			boolean singular = expected.equals("1") || expected.endsWith(" 1");
			throw new CalcException(c.name + "() takes " + expected + " argument" + (singular ? "" : "s") + ", got " + n, c.pos);
		}
		double[] args = new double[n];
		for (int k = 0; k < n; k++) {
			args[k] = eval(c.args.get(k));
		}
		return fn.impl.apply(this, args, c.pos);
	}

	private static double check(double value, int pos) throws CalcException {
		if (Double.isNaN(value)) {
			throw new CalcException("The result is undefined", pos);
		}
		if (Double.isInfinite(value)) {
			throw new CalcException("The result is too large", pos);
		}
		return value;
	}

	// math helpers

	private double toRadians(double x) {
		return angleUnit == AngleUnit.DEGREES ? Math.toRadians(x) : x;
	}

	private double fromRadians(double x) {
		return angleUnit == AngleUnit.DEGREES ? Math.toDegrees(x) : x;
	}

	// Removes floating point dust from trig results, so sin(180) is 0 and not 1.2e-16
	private static double snap(double x) {
		if (Math.abs(x) < 1e-14) {
			return 0;
		}
		double rounded = Math.rint(x);
		return Math.abs(x - rounded) < 1e-14 ? rounded : x;
	}

	private static boolean isInteger(double x) {
		return x == Math.rint(x) && !Double.isInfinite(x);
	}

	private static double factorial(double n, int pos) throws CalcException {
		if (n < 0 && isInteger(n)) {
			throw new CalcException("Factorial of a negative integer is undefined", pos);
		}
		if (n > 170) {
			throw new CalcException("Factorial is too large", pos);
		}
		if (isInteger(n)) {
			double result = 1;
			for (int k = 2; k <= (int) n; k++) {
				result *= k;
			}
			return result;
		}
		return gamma(n + 1);
	}

	private static final double[] LANCZOS = {
		0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
		-176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7
	};

	private static double gamma(double x) {
		if (x < 0.5) {
			return Math.PI / (Math.sin(Math.PI * x) * gamma(1 - x));
		}
		x -= 1;
		double a = LANCZOS[0];
		double t = x + 7.5;
		for (int k = 1; k < 9; k++) {
			a += LANCZOS[k] / (x + k);
		}
		return Math.sqrt(2 * Math.PI) * Math.pow(t, x + 0.5) * Math.exp(-t) * a;
	}

	private static long requireLong(double x, String fn, int pos) throws CalcException {
		if (!isInteger(x) || Math.abs(x) > 9.0e15) {
			throw new CalcException(fn + "() needs whole numbers", pos);
		}
		return (long) x;
	}

	private static long gcd(long a, long b) {
		a = Math.abs(a);
		b = Math.abs(b);
		while (b != 0) {
			long t = a % b;
			a = b;
			b = t;
		}
		return a;
	}

	// built-ins

	@FunctionalInterface
	private interface Impl {
		double apply(Calculator calc, double[] a, int pos) throws CalcException;
	}

	private record Fn(int minArgs, int maxArgs, Impl impl) {
	}

	private static void fn(String name, int min, int max, Impl impl) {
		FUNCTIONS.put(name, new Fn(min, max, impl));
	}

	private static void fn1(String name, java.util.function.DoubleUnaryOperator op) {
		fn(name, 1, 1, (c, a, p) -> op.applyAsDouble(a[0]));
	}

	private static final int VARARGS = Integer.MAX_VALUE;

	static {
		CONSTANTS.put("pi", Math.PI);
		CONSTANTS.put("π", Math.PI);
		CONSTANTS.put("tau", Math.PI * 2);
		CONSTANTS.put("τ", Math.PI * 2);
		CONSTANTS.put("e", Math.E);
		CONSTANTS.put("phi", (1 + Math.sqrt(5)) / 2);
		CONSTANTS.put("φ", (1 + Math.sqrt(5)) / 2);

		// Minecraft-flavoured units, usable as "3sb + 5st"
		UNITS.put("st", 64.0);
		UNITS.put("stack", 64.0);
		UNITS.put("stacks", 64.0);
		UNITS.put("sb", 1728.0);
		UNITS.put("shulker", 1728.0);
		UNITS.put("shulkers", 1728.0);
		UNITS.put("chest", 1728.0);
		UNITS.put("chests", 1728.0);
		UNITS.put("dc", 3456.0);
		UNITS.put("dchest", 3456.0);
		UNITS.put("k", 1_000.0);

		fn("sqrt", 1, 1, (c, a, p) -> {
			if (a[0] < 0) {
				throw new CalcException("Square root of a negative number", p);
			}
			return Math.sqrt(a[0]);
		});
		fn1("cbrt", Math::cbrt);
		fn1("abs", Math::abs);
		fn1("sign", Math::signum);
		fn1("sgn", Math::signum);
		fn1("floor", Math::floor);
		fn1("ceil", Math::ceil);
		fn1("trunc", x -> x < 0 ? Math.ceil(x) : Math.floor(x));
		fn1("frac", x -> x - (x < 0 ? Math.ceil(x) : Math.floor(x)));
		fn("round", 1, 2, (c, a, p) -> {
			if (a.length == 1) {
				return (double) Math.round(a[0]);
			}
			int digits = (int) requireLong(a[1], "round", p);
			if (digits < -15 || digits > 15) {
				throw new CalcException("round() can keep between -15 and 15 digits", p);
			}
			if (!Double.isFinite(a[0])) {
				throw new CalcException("The result is too large", p);
			}
			return new java.math.BigDecimal(a[0]).setScale(digits, java.math.RoundingMode.HALF_UP).doubleValue();
		});

		fn("sin", 1, 1, (c, a, p) -> snap(Math.sin(c.toRadians(a[0]))));
		fn("cos", 1, 1, (c, a, p) -> snap(Math.cos(c.toRadians(a[0]))));
		fn("tan", 1, 1, (c, a, p) -> {
			double r = c.toRadians(a[0]);
			if (Math.abs(snap(Math.cos(r))) == 0) {
				throw new CalcException("tan() is undefined here", p);
			}
			return snap(Math.tan(r));
		});
		fn("asin", 1, 1, (c, a, p) -> {
			if (a[0] < -1 || a[0] > 1) {
				throw new CalcException("asin() needs a value between -1 and 1", p);
			}
			return snap(c.fromRadians(Math.asin(a[0])));
		});
		fn("acos", 1, 1, (c, a, p) -> {
			if (a[0] < -1 || a[0] > 1) {
				throw new CalcException("acos() needs a value between -1 and 1", p);
			}
			return snap(c.fromRadians(Math.acos(a[0])));
		});
		fn("atan", 1, 1, (c, a, p) -> snap(c.fromRadians(Math.atan(a[0]))));
		fn("atan2", 2, 2, (c, a, p) -> snap(c.fromRadians(Math.atan2(a[0], a[1]))));
		fn1("sinh", Math::sinh);
		fn1("cosh", Math::cosh);
		fn1("tanh", Math::tanh);

		fn("ln", 1, 1, (c, a, p) -> {
			if (a[0] <= 0) {
				throw new CalcException("ln() needs a positive number", p);
			}
			return Math.log(a[0]);
		});
		fn("log", 1, 2, (c, a, p) -> {
			if (a[0] <= 0) {
				throw new CalcException("log() needs a positive number", p);
			}
			if (a.length == 1) {
				return snap(Math.log10(a[0]));
			}
			if (a[1] <= 0 || a[1] == 1) {
				throw new CalcException("log() base must be positive and not 1", p);
			}
			return snap(Math.log(a[0]) / Math.log(a[1]));
		});
		fn("log10", 1, 1, (c, a, p) -> {
			if (a[0] <= 0) {
				throw new CalcException("log10() needs a positive number", p);
			}
			return snap(Math.log10(a[0]));
		});
		fn("log2", 1, 1, (c, a, p) -> {
			if (a[0] <= 0) {
				throw new CalcException("log2() needs a positive number", p);
			}
			return snap(Math.log(a[0]) / Math.log(2));
		});
		fn1("exp", Math::exp);
		fn("pow", 2, 2, (c, a, p) -> {
			double r = Math.pow(a[0], a[1]);
			if (Double.isNaN(r)) {
				throw new CalcException("No real result", p);
			}
			return r;
		});
		fn("hypot", 2, VARARGS, (c, a, p) -> {
			double sum = 0;
			for (double v : a) {
				sum += v * v;
			}
			return Math.sqrt(sum);
		});

		fn("min", 1, VARARGS, (c, a, p) -> {
			double m = a[0];
			for (double v : a) {
				m = Math.min(m, v);
			}
			return m;
		});
		fn("max", 1, VARARGS, (c, a, p) -> {
			double m = a[0];
			for (double v : a) {
				m = Math.max(m, v);
			}
			return m;
		});
		fn("sum", 1, VARARGS, (c, a, p) -> {
			double s = 0;
			for (double v : a) {
				s += v;
			}
			return s;
		});
		Impl avg = (c, a, p) -> {
			double s = 0;
			for (double v : a) {
				s += v;
			}
			return s / a.length;
		};
		fn("avg", 1, VARARGS, avg);
		fn("mean", 1, VARARGS, avg);

		Impl fact = (c, a, p) -> factorial(a[0], p);
		fn("fact", 1, 1, fact);
		fn("factorial", 1, 1, fact);
		Impl choose = (c, a, p) -> {
			long n = requireLong(a[0], "ncr", p);
			long k = requireLong(a[1], "ncr", p);
			if (k < 0 || n < 0 || k > n) {
				return 0;
			}
			k = Math.min(k, n - k);
			double r = 1;
			for (long j = 1; j <= k; j++) {
				r = r * (n - k + j) / j;
			}
			return Math.rint(r);
		};
		fn("ncr", 2, 2, choose);
		fn("choose", 2, 2, choose);
		fn("npr", 2, 2, (c, a, p) -> {
			long n = requireLong(a[0], "npr", p);
			long k = requireLong(a[1], "npr", p);
			if (k < 0 || n < 0 || k > n) {
				return 0;
			}
			double r = 1;
			for (long j = 0; j < k; j++) {
				r *= n - j;
			}
			return r;
		});
		fn("gcd", 2, VARARGS, (c, a, p) -> {
			long g = requireLong(a[0], "gcd", p);
			for (int k = 1; k < a.length; k++) {
				g = gcd(g, requireLong(a[k], "gcd", p));
			}
			return g;
		});
		fn("lcm", 2, VARARGS, (c, a, p) -> {
			long l = requireLong(a[0], "lcm", p);
			for (int k = 1; k < a.length; k++) {
				long v = requireLong(a[k], "lcm", p);
				if (l == 0 || v == 0) {
					return 0;
				}
				l = Math.abs(l / gcd(l, v) * v);
			}
			return l;
		});

		fn("clamp", 3, 3, (c, a, p) -> Math.max(a[1], Math.min(a[2], a[0])));
		fn("lerp", 3, 3, (c, a, p) -> a[0] + (a[1] - a[0]) * a[2]);
		fn1("deg", Math::toDegrees);
		fn1("rad", Math::toRadians);
		Impl rand = (c, a, p) -> a.length == 0 ? c.random.nextDouble() : c.random.nextDouble() * a[0];
		fn("rand", 0, 1, rand);
		fn("random", 0, 1, rand);
		fn("randint", 2, 2, (c, a, p) -> {
			long lo = requireLong(Math.min(a[0], a[1]), "randint", p);
			long hi = requireLong(Math.max(a[0], a[1]), "randint", p);
			return lo + (long) Math.floor(c.random.nextDouble() * (hi - lo + 1));
		});

		// Minecraft helpers
		fn("nether", 1, 1, (c, a, p) -> a[0] / 8.0);
		fn("overworld", 1, 1, (c, a, p) -> a[0] * 8.0);
		fn("chunk", 1, 1, (c, a, p) -> Math.floor(a[0] / 16.0));
		fn("region", 1, 1, (c, a, p) -> Math.floor(a[0] / 512.0));
		fn("stacks", 1, 2, (c, a, p) -> {
			double size = a.length == 2 ? a[1] : 64;
			if (size <= 0) {
				throw new CalcException("Stack size must be positive", p);
			}
			return a[0] / size;
		});
		fn("dist", 4, 6, (c, a, p) -> {
			if (a.length == 4) {
				return Math.hypot(a[2] - a[0], a[3] - a[1]);
			}
			if (a.length == 6) {
				double dx = a[3] - a[0];
				double dy = a[4] - a[1];
				double dz = a[5] - a[2];
				return Math.sqrt(dx * dx + dy * dy + dz * dz);
			}
			throw new CalcException("dist() takes (x1, z1, x2, z2) or (x1, y1, z1, x2, y2, z2)", p);
		});
	}

	// Every built-in name, used by the help text
	public static Map<String, String> describeBuiltins() {
		Map<String, String> m = new HashMap<>();
		m.put("functions", String.join(", ", FUNCTIONS.keySet()));
		m.put("constants", "pi, tau, e, phi");
		m.put("units", "st/stack (64), sb/shulker/chest (1728), dc (3456), k (1000)");
		return m;
	}
}

package io.github.ningmeng123.mite.rules;

import java.util.ArrayList;
import java.util.List;

/** Bounded arithmetic expressions for configurable monster growth. @author NingMeng123 */
public final class GrowthFormula {
    private static final int MAX_LENGTH = 512;
    private static final int MAX_TOKENS = 256;
    private static final int MAX_NODES = 256;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_SAMPLES = 4096;
    private static final List<Context> DEFAULT_SAMPLES = defaultSamples();

    private GrowthFormula() {}

    /** The caller supplies the attribute basis and calendar values; expressions cannot access game objects. */
    public record Context(double base, double initial, double reference, double rate,
                          double days, double day, double blood_moons) {}

    public record Validation(boolean valid, String message) {}

    /** Immutable, reusable compiled expression. All nodes are arithmetic operations from the allowlist below. */
    public static final class Compiled {
        private final Node root;

        private Compiled(Node root) { this.root = root; }

        /** Zero is permitted for damage; negative final results and every nonfinite intermediate are rejected. */
        public double evaluate(Context context) {
            if (context == null) throw new IllegalArgumentException("公式计算缺少变量上下文");
            for (double value : new double[]{context.base(), context.initial(), context.reference(), context.rate(),
                    context.days(), context.day(), context.blood_moons()}) {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("公式变量必须为有限数值");
            }
            double result = root.evaluate(context);
            if (result < 0D) throw error(root.position, "公式计算结果不能为负数");
            return result;
        }
    }

    /** Compiles syntax only. Use validate before saving a formula to also check representative execution. */
    public static Compiled compile(String expression) {
        if (expression == null || expression.isBlank()) throw new IllegalArgumentException("增长公式不能为空");
        if (expression.length() > MAX_LENGTH) throw new IllegalArgumentException("增长公式最多允许 " + MAX_LENGTH + " 个字符");
        return new Compiled(new Parser(expression).parse());
    }

    /** Requires a positive result over representative early, rebased, late and zero-growth contexts. */
    public static Validation validate(String expression) {
        return validate(expression, DEFAULT_SAMPLES, false);
    }

    public static Validation validate(String expression, List<Context> samples) {
        return validate(expression, samples, false);
    }

    /** Allows a zero result only for callers whose attribute supports zero, such as attack damage. */
    public static Validation validate(String expression, List<Context> samples, boolean allowZero) {
        try {
            Compiled compiled = compile(expression);
            if (samples == null || samples.isEmpty()) throw new IllegalArgumentException("公式校验至少需要一组运行样本");
            if (samples.size() > MAX_SAMPLES) throw new IllegalArgumentException("公式校验样本不能超过 " + MAX_SAMPLES + " 组");
            for (Context sample : samples) {
                try {
                    double result = compiled.evaluate(sample);
                    if (!allowZero && result == 0D) throw new IllegalArgumentException("公式计算结果必须大于 0");
                } catch (IllegalArgumentException exception) {
                    return new Validation(false, exception.getMessage() + describe(sample));
                }
            }
            return new Validation(true, "公式语法与试算均通过");
        } catch (IllegalArgumentException exception) {
            return new Validation(false, exception.getMessage());
        }
    }

    private static String describe(Context context) {
        if (context == null) return "（校验样本为空）";
        return "（试算：base=" + context.base() + ", initial=" + context.initial() + ", reference=" + context.reference()
                + ", rate=" + context.rate() + ", days=" + context.days() + ", day=" + context.day()
                + ", blood_moons=" + context.blood_moons() + "）";
    }

    private static List<Context> defaultSamples() {
        List<Context> samples = new ArrayList<>();
        for (double base : new double[]{6D, 20D, 30D, 1024D})
            for (double reference : new double[]{6D, 20D, 30D, 1024D})
                for (double rate : new double[]{0D, .005D, .0025D, 1D})
                    for (double days : new double[]{0D, 1D, 31D, 32D, 64D})
                        for (double day : new double[]{1D, 33D, 129D, 1025D})
                            samples.add(new Context(base, base, reference, rate, days, day, Math.floor((day - 1D) / 32D)));
        samples.add(new Context(9.2D, 6D, 20D, .005D, 0D, 33D, 1D));
        samples.add(new Context(23.2D, 20D, 23.2D, .005D, 31D, 64D, 1D));
        return List.copyOf(samples);
    }

    private enum Kind { NUMBER, VARIABLE, UNARY, BINARY, FUNCTION }

    private static final class Node {
        private final Kind kind;
        private final String text;
        private final double number;
        private final int position;
        private final Node[] children;
        private final int depth;

        private Node(Kind kind, String text, double number, int position, Node... children) {
            this.kind = kind;
            this.text = text;
            this.number = number;
            this.position = position;
            this.children = children;
            int maximumChildDepth = 0;
            for (Node child : children) maximumChildDepth = Math.max(maximumChildDepth, child.depth);
            depth = maximumChildDepth + 1;
        }

        private double evaluate(Context context) {
            double result = switch (kind) {
                case NUMBER -> number;
                case VARIABLE -> switch (text) {
                    case "base" -> context.base();
                    case "initial" -> context.initial();
                    case "reference" -> context.reference();
                    case "rate" -> context.rate();
                    case "days" -> context.days();
                    case "day" -> context.day();
                    case "blood_moons" -> context.blood_moons();
                    default -> throw error(position, "未知变量：" + text);
                };
                case UNARY -> text.equals("-") ? -children[0].evaluate(context) : children[0].evaluate(context);
                case BINARY -> binary(children[0].evaluate(context), children[1].evaluate(context));
                case FUNCTION -> function(context);
            };
            if (!Double.isFinite(result)) throw error(position, "计算产生无效或无限数值，请检查定义域与数值范围");
            return result;
        }

        private double binary(double left, double right) {
            return switch (text) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*" -> left * right;
                case "/" -> {
                    if (right == 0D) throw error(position, "除数不能为 0");
                    yield left / right;
                }
                case "%" -> {
                    if (right == 0D) throw error(position, "取余除数不能为 0");
                    yield left % right;
                }
                case "^" -> Math.pow(left, right);
                default -> throw error(position, "不支持的运算符：" + text);
            };
        }

        private double function(Context context) {
            double first = children[0].evaluate(context);
            double second = children.length > 1 ? children[1].evaluate(context) : 0D;
            return switch (text) {
                case "abs" -> Math.abs(first);
                case "sqrt" -> Math.sqrt(first);
                case "log" -> Math.log(first);
                case "exp" -> Math.exp(first);
                case "floor" -> Math.floor(first);
                case "ceil" -> Math.ceil(first);
                case "min" -> Math.min(first, second);
                case "max" -> Math.max(first, second);
                case "pow" -> Math.pow(first, second);
                case "clamp" -> {
                    double third = children[2].evaluate(context);
                    if (second > third) throw error(position, "clamp 的下限不能大于上限");
                    yield Math.max(second, Math.min(first, third));
                }
                default -> throw error(position, "未知函数：" + text);
            };
        }
    }

    private record Token(String text, int position, boolean number) {}

    private static final class Parser {
        private final String expression;
        private int offset;
        private int tokenCount;
        private int nodeCount;
        private Token current;

        private Parser(String expression) {
            this.expression = expression;
            advance();
        }

        private Node parse() {
            Node result = additive(0);
            if (!current.text().isEmpty()) throw error(current.position(), "此处存在多余内容：" + current.text());
            return result;
        }

        private Node additive(int depth) {
            Node result = multiplicative(depth);
            while (is("+") || is("-")) {
                Token operator = consume();
                result = node(Kind.BINARY, operator, 0D, result, multiplicative(depth));
            }
            return result;
        }

        private Node multiplicative(int depth) {
            Node result = unary(depth);
            while (is("*") || is("/") || is("%")) {
                Token operator = consume();
                result = node(Kind.BINARY, operator, 0D, result, unary(depth));
            }
            return result;
        }

        private Node unary(int depth) {
            if (depth >= MAX_DEPTH) throw error(current.position(), "公式嵌套过深，最多允许 " + MAX_DEPTH + " 层");
            if (is("+") || is("-")) {
                Token operator = consume();
                return node(Kind.UNARY, operator, 0D, unary(depth + 1));
            }
            Node result = primary(depth);
            if (is("^")) {
                Token operator = consume();
                result = node(Kind.BINARY, operator, 0D, result, unary(depth + 1));
            }
            return result;
        }

        private Node primary(int depth) {
            Token token = consume();
            if (token.number()) {
                double value;
                try { value = Double.parseDouble(token.text()); }
                catch (NumberFormatException exception) { throw error(token.position(), "无效数字：" + token.text()); }
                if (!Double.isFinite(value)) throw error(token.position(), "数字超出有限数值范围");
                return node(Kind.NUMBER, token, value);
            }
            if (token.text().equals("(")) {
                Node result = additive(depth + 1);
                expect(")");
                return result;
            }
            if (!token.text().isEmpty() && isIdentifierStart(token.text().charAt(0))) {
                if (is("(")) return function(token, depth);
                if (!List.of("base", "initial", "reference", "rate", "days", "day", "blood_moons").contains(token.text()))
                    throw error(token.position(), "未知变量：" + token.text());
                return node(Kind.VARIABLE, token, 0D);
            }
            throw error(token.position(), "此处需要数字、变量或括号表达式");
        }

        private Node function(Token name, int depth) {
            int arity = switch (name.text()) {
                case "abs", "sqrt", "log", "exp", "floor", "ceil" -> 1;
                case "min", "max", "pow" -> 2;
                case "clamp" -> 3;
                default -> throw error(name.position(), "未知函数：" + name.text());
            };
            consume(); // Opening parenthesis.
            Node[] arguments = new Node[arity];
            for (int index = 0; index < arity; index++) {
                if (index > 0) expect(",");
                arguments[index] = additive(depth + 1);
            }
            if (!is(")")) throw error(current.position(), name.text() + " 函数需要 " + arity + " 个参数并以 ) 结束");
            consume();
            return node(Kind.FUNCTION, name, 0D, arguments);
        }

        private Node node(Kind kind, Token token, double number, Node... children) {
            if (++nodeCount > MAX_NODES) throw error(token.position(), "公式运算数量过多");
            Node result = new Node(kind, token.text(), number, token.position(), children);
            if (result.depth > MAX_DEPTH) throw error(token.position(), "公式运算树过深，最多允许 " + MAX_DEPTH + " 层");
            return result;
        }

        private boolean is(String text) { return current.text().equals(text); }

        private Token consume() {
            Token result = current;
            advance();
            return result;
        }

        private void expect(String text) {
            if (!is(text)) throw error(current.position(), "此处需要 " + text);
            consume();
        }

        private void advance() {
            while (offset < expression.length() && Character.isWhitespace(expression.charAt(offset))) offset++;
            int start = offset;
            if (offset == expression.length()) {
                current = new Token("", offset, false);
                return;
            }
            if (++tokenCount > MAX_TOKENS) throw error(start, "公式符号数量过多");
            char character = expression.charAt(offset++);
            if (isDigit(character) || character == '.') {
                while (offset < expression.length() && (isDigit(expression.charAt(offset)) || expression.charAt(offset) == '.')) offset++;
                if (offset < expression.length() && (expression.charAt(offset) == 'e' || expression.charAt(offset) == 'E')) {
                    offset++;
                    if (offset < expression.length() && (expression.charAt(offset) == '+' || expression.charAt(offset) == '-')) offset++;
                    while (offset < expression.length() && isDigit(expression.charAt(offset))) offset++;
                }
                if (offset - start > 64) throw error(start, "数字字面量过长");
                current = new Token(expression.substring(start, offset), start, true);
            } else if (isIdentifierStart(character)) {
                while (offset < expression.length() && (isIdentifierStart(expression.charAt(offset)) || isDigit(expression.charAt(offset)))) offset++;
                if (offset - start > 32) throw error(start, "变量或函数名称过长");
                current = new Token(expression.substring(start, offset), start, false);
            } else if ("+-*/%^(),".indexOf(character) >= 0) {
                current = new Token(String.valueOf(character), start, false);
            } else {
                throw error(start, "不支持的字符：" + character);
            }
        }
    }

    private static boolean isIdentifierStart(char character) {
        return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z' || character == '_';
    }

    private static boolean isDigit(char character) { return character >= '0' && character <= '9'; }

    private static IllegalArgumentException error(int position, String message) {
        return new IllegalArgumentException("第 " + (position + 1) + " 个字符：" + message);
    }
}

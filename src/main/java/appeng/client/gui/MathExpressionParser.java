package appeng.client.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

public class MathExpressionParser {
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);
    private static final BigDecimal ONE_BILLION = BigDecimal.valueOf(1e9);

    public static Optional<BigDecimal> parse(String expression, DecimalFormat decimalFormat) {
        expression = normalizeInput(expression);

        // Parse using the Shunting Yard Algorithm

        List<Object> output = new ArrayList<>();
        Stack<Character> operatorStack = new Stack<>();
        boolean wasNumberOrRightBracket = false;

        for (int i = 0; i < expression.length();) {
            if (Character.isWhitespace(expression.charAt(i))) {
                i++;
                continue;
            }

            if (!wasNumberOrRightBracket && expression.charAt(i) != '-') {
                var parsedNumber = parseNumber(expression, i);
                if (parsedNumber.hasError()) {
                    return Optional.empty();
                } else if (parsedNumber.token() != null) {
                    output.add(parsedNumber.token().value());
                    i = parsedNumber.token().end();
                    wasNumberOrRightBracket = true;
                    continue;
                }
            }

            char currentOperator = expression.charAt(i);
            if (currentOperator == '-' && !wasNumberOrRightBracket) {
                currentOperator = 'u'; // unitary minus
            }

            wasNumberOrRightBracket = false;

            switch (currentOperator) {
                case '(', 'u' -> {
                    operatorStack.push(currentOperator);
                }
                case ')' -> {
                    while (true) {
                        if (operatorStack.isEmpty()) {
                            return Optional.empty(); // mismatched parenthesis
                        }
                        char operator = operatorStack.pop();
                        if (operator == '(') {
                            break;
                        } else {
                            output.add(operator);
                        }
                    }
                    wasNumberOrRightBracket = true;
                }
                case '+', '-', '*', '/', '^' -> {
                    while (!operatorStack.isEmpty()) {
                        char operator = operatorStack.peek();
                        if (operator != '(' && precedenceCheck(operator, currentOperator)) {
                            operatorStack.pop();
                            output.add(operator);
                        } else {
                            break;
                        }
                    }
                    operatorStack.push(currentOperator);
                }
                default -> {
                    return Optional.empty();
                }

            }
            i++;

        }

        while (!operatorStack.isEmpty()) {
            var operator = operatorStack.pop();
            if (operator == '(') {
                return Optional.empty(); // mismatched parenthesis
            }
            output.add(operator);
        }

        Stack<BigDecimal> number = new Stack<>();

        for (Object object : output) {
            if (object instanceof BigDecimal bigDecimal) {
                number.push(bigDecimal);
            } else {
                char currentOperator = (char) object;
                if (currentOperator != 'u') {
                    if (number.size() < 2) {
                        return Optional.empty();
                    } else {
                        BigDecimal right = number.pop();
                        BigDecimal left = number.pop();
                        switch (currentOperator) {
                            case '+' -> {
                                number.push(right.add(left));
                            }
                            case '*' -> {
                                number.push(right.multiply(left));
                            }
                            case '-' -> {
                                number.push(left.subtract(right));
                            }
                            case '/' -> {
                                if (right.compareTo(BigDecimal.ZERO) == 0) {
                                    return Optional.empty(); // division by zeroes
                                } else {
                                    number.push(left.divide(right, 8, RoundingMode.FLOOR));
                                }
                            }
                            case '^' -> {
                                right = right.stripTrailingZeros();
                                // if has a decimal part or is smaller than 0 -> nope
                                if (right.scale() > 0 || right.compareTo(BigDecimal.ZERO) < 0) {
                                    return Optional.empty();
                                }
                                // limit exponent to 30
                                if (right.compareTo(THIRTY) > 0) {
                                    return Optional.empty(); // exponent too big
                                }
                                // limit base number to 1e9
                                if (left.compareTo(ONE_BILLION) > 0) {
                                    return Optional.empty(); // base number too big
                                }
                                number.push(left.pow(right.intValueExact()));
                            }
                            case '(', ')' -> {
                                return Optional.empty(); // should not have any remaining parenthesis in the stack
                            }
                            default -> {
                                throw new IllegalStateException("Unreachable character : " + currentOperator);
                            }
                        }
                    }
                } else {
                    if (number.isEmpty()) {
                        return Optional.empty();
                    } else {
                        number.push(number.pop().negate());
                    }
                }
            }
        }

        if (number.size() != 1) {
            return Optional.empty();
        } else {
            return Optional.of(number.pop().stripTrailingZeros());
        }

    }

    private static int getPrecedence(char operator) {
        return switch (operator) {
            case '^' -> -1;
            case 'u' -> 0;
            case '/', '*' -> 1;
            case '+', '-' -> 2;
            default -> throw new IllegalArgumentException("Invalid Operator : " + operator);
        };
    }

    private static boolean precedenceCheck(char first, char second) {
        return getPrecedence(first) <= getPrecedence(second);
    }

    public static String normalizeInput(String expression) {
        StringBuilder result = null;

        for (int i = 0; i < expression.length(); i++) {
            char original = expression.charAt(i);
            char normalized = normalizeChar(original);
            if (result != null) {
                result.append(normalized);
            } else if (normalized != original) {
                result = new StringBuilder(expression.length());
                result.append(expression, 0, i);
                result.append(normalized);
            }
        }

        return result != null ? result.toString() : expression;
    }

    private static char normalizeChar(char c) {
        if (c >= '０' && c <= '９') {
            return (char) ('0' + c - '０');
        }

        return switch (c) {
            case '（' -> '(';
            case '）' -> ')';
            case '，' -> ',';
            case '．' -> '.';
            case '＋' -> '+';
            case '－', '−' -> '-';
            case '＊', '×' -> '*';
            case '／' -> '/';
            case '＾' -> '^';
            case 'ｅ' -> 'e';
            default -> c;
        };
    }

    private static NumberParseResult parseNumber(String expression, int start) {
        char first = expression.charAt(start);
        if (!isDigit(first) && first != '.') {
            return NumberParseResult.none();
        }

        int i = start;
        StringBuilder number = new StringBuilder();
        List<Integer> groups = new ArrayList<>();
        char separator = 0;
        int groupSize = 0;
        boolean hasIntegerDigit = false;
        boolean lastWasSeparator = false;

        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (isDigit(c)) {
                number.append(c);
                groupSize++;
                hasIntegerDigit = true;
                lastWasSeparator = false;
                i++;
            } else if (c == ',' || c == '_') {
                if (!hasIntegerDigit || lastWasSeparator) {
                    return NumberParseResult.invalid();
                }
                if (separator == 0) {
                    separator = c;
                } else if (separator != c) {
                    return NumberParseResult.invalid();
                }
                groups.add(groupSize);
                groupSize = 0;
                lastWasSeparator = true;
                i++;
            } else {
                break;
            }
        }

        if (lastWasSeparator) {
            return NumberParseResult.invalid();
        }

        if (separator != 0) {
            groups.add(groupSize);
            if (groups.get(0) < 1 || groups.get(0) > 3) {
                return NumberParseResult.invalid();
            }
            for (int group = 1; group < groups.size(); group++) {
                if (groups.get(group) != 3) {
                    return NumberParseResult.invalid();
                }
            }
        }

        int decimalDigits = 0;
        if (i < expression.length() && expression.charAt(i) == '.') {
            number.append('.');
            i++;
            while (i < expression.length() && isDigit(expression.charAt(i))) {
                number.append(expression.charAt(i));
                decimalDigits++;
                i++;
            }
        }

        if (!hasIntegerDigit && decimalDigits == 0) {
            return NumberParseResult.invalid();
        }

        if (i < expression.length() && expression.charAt(i) == 'e') {
            number.append('e');
            i++;

            if (i < expression.length() && (expression.charAt(i) == '+' || expression.charAt(i) == '-')) {
                number.append(expression.charAt(i));
                i++;
            }

            int exponentDigits = 0;
            while (i < expression.length() && isDigit(expression.charAt(i))) {
                number.append(expression.charAt(i));
                exponentDigits++;
                i++;
            }

            if (exponentDigits == 0) {
                return NumberParseResult.invalid();
            }
        }

        try {
            return NumberParseResult.of(new NumberToken(new BigDecimal(number.toString()), i));
        } catch (NumberFormatException e) {
            return NumberParseResult.invalid();
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private record NumberToken(BigDecimal value, int end) {
    }

    private record NumberParseResult(NumberToken token, boolean hasError) {
        static NumberParseResult of(NumberToken token) {
            return new NumberParseResult(token, false);
        }

        static NumberParseResult none() {
            return new NumberParseResult(null, false);
        }

        static NumberParseResult invalid() {
            return new NumberParseResult(null, true);
        }
    }

}

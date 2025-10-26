package impl.project2;

import framework.project2.Grader;
import framework.project2.MissingSymbolError;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.misc.IntervalSet;
import generated.Splc.SplcParser;

import java.util.List;

public class Project2ErrorListener extends BaseErrorListener {
    private final Grader grader;

    public Project2ErrorListener(Grader grader) {
        this.grader = grader;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        System.err.println("DEBUG- Message:" + msg);
        SplcParser parser = (SplcParser) recognizer;
        IntervalSet expected = parser.getExpectedTokens();
        String tokenName = null;
        int reportLine = line;

        Vocabulary vocabulary = recognizer.getVocabulary();

        System.err.println("Expected tokens: " + expected.toString(vocabulary));
        System.err.println("Offending symbol: " + offendingSymbol);
        System.err.println("Error at line " + line + ":" + charPositionInLine);

        if (offendingSymbol instanceof Token) {
            Token token = (Token) offendingSymbol;
            System.err.println("Offending token type: " + vocabulary.getSymbolicName(token.getType()));
            System.err.println("Offending token text: '" + token.getText() + "'");
            System.err.println("Offending token line: " + token.getLine());
        }

        // 特殊处理EOF错误
        if (offendingSymbol instanceof Token && ((Token) offendingSymbol).getType() == Token.EOF) {
            handleEOFError(recognizer, (Token) offendingSymbol, msg, e);
            return;
        }

        // 按优先级检查各种错误类型
        if (isMissingSemicolonInStruct(recognizer, offendingSymbol, expected, vocabulary, msg)) {
            tokenName = "SEMI";
            reportLine = getPreviousTokenLine(recognizer, offendingSymbol);
        } else if (isMissingLeftParenthesis(recognizer, offendingSymbol, expected, vocabulary, msg)) {
            tokenName = "LPAREN";
            reportLine = calculateReportLine(line, offendingSymbol, parser);
        } else if (isMissingFunctionCallParenthesis(recognizer, offendingSymbol, expected, vocabulary, msg)) {
            tokenName = "LPAREN";
            reportLine = calculateReportLine(line, offendingSymbol, parser);
        }else if (isMissingLeftBracket(recognizer, offendingSymbol, expected, vocabulary, msg)) {
            tokenName = "LBRACK";
            reportLine = calculateReportLine(line, offendingSymbol, parser);
        } else if (isMissingLeftBrace(recognizer, offendingSymbol, expected, vocabulary, msg)) {
            tokenName = "LBRACE";
            reportLine = calculateReportLine(line, offendingSymbol, parser);
        } else {
            // 其他情况使用通用处理逻辑
            tokenName = findPrioritizedToken(expected, vocabulary);
            reportLine = calculateReportLine(line, offendingSymbol, parser);
        }

        if (tokenName == null) {
            return;
        }

        MissingSymbolError missingSymbol = new MissingSymbolError(tokenName, reportLine);
        this.grader.getWriter().println(missingSymbol);
        throw new ParseCancellationException();
    }

    // ==================== 工具方法 ====================

    /**
     * 计算报告行号
     */
    private int calculateReportLine(int line, Object offendingSymbol, Parser parser) {
        int reportLine = Math.max(0, line - 1);
        if (offendingSymbol instanceof Token && parser.getInputStream() instanceof CommonTokenStream ts) {
            for (int i = ((Token) offendingSymbol).getTokenIndex() - 1; i >= 0; i--) {
                Token prev = ts.get(i);
                if (prev.getChannel() == Token.DEFAULT_CHANNEL) {
                    reportLine = Math.max(0, prev.getLine() - 1);
                    break;
                }
            }
        }
        return reportLine;
    }

    /**
     * 获取前一个token的行号（用于结构体内部缺少分号的情况）
     */
    private int getPreviousTokenLine(Recognizer<?, ?> recognizer, Object offendingSymbol) {
        if (offendingSymbol instanceof Token && recognizer instanceof SplcParser) {
            SplcParser parser = (SplcParser) recognizer;
            if (parser.getInputStream() instanceof CommonTokenStream) {
                CommonTokenStream ts = (CommonTokenStream) parser.getInputStream();
                Token offendingToken = (Token) offendingSymbol;
                for (int i = offendingToken.getTokenIndex() - 1; i >= 0; i--) {
                    Token prev = ts.get(i);
                    if (prev.getChannel() == Token.DEFAULT_CHANNEL) {
                        return prev.getLine();
                    }
                }
            }
        }
        return 0;
    }

    /**
     * 查找优先的token名称
     */
    private String findPrioritizedToken(IntervalSet expected, Vocabulary vocabulary) {
        // 优先检查常见的结构结束符号
        String[] prioritizedTokens = {"')'", "'}'", "';'"};

        for (String prioritizedToken : prioritizedTokens) {
            for (int tokenType : expected.toArray()) {
                if (tokenType == Token.EOF) continue;

                String currentTokenName = vocabulary.getSymbolicName(tokenType);
                String displayName = vocabulary.getDisplayName(tokenType);

                if (prioritizedToken.equals(displayName) ||
                        prioritizedToken.replace("'", "").equals(currentTokenName)) {
                    return currentTokenName;
                }
            }
        }

        // 如果没有找到优先的token，返回第一个非EOF的期望token
        for (int tokenType : expected.toArray()) {
            if (tokenType != Token.EOF) {
                return vocabulary.getSymbolicName(tokenType);
            }
        }

        return null;
    }

    // ==================== 错误检测方法 ====================

    /**
     * 检查是否是结构体内部缺少分号的错误
     */
    private boolean isMissingSemicolonInStruct(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                               IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!msg.contains("no viable alternative")) {
            return false;
        }

        // 检查期望的token是否包含声明关键字
        boolean hasDeclKeywords = false;
        for (int tokenType : expected.toArray()) {
            String symbolName = vocabulary.getSymbolicName(tokenType);
            if ("INT".equals(symbolName) || "CHAR".equals(symbolName) || "STRUCT".equals(symbolName)) {
                hasDeclKeywords = true;
                break;
            }
        }

        if (!hasDeclKeywords) {
            return false;
        }

        // 检查前一个token是否是结构体成员声明的一部分
        if (recognizer instanceof SplcParser &&
                ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream &&
                offendingSymbol instanceof Token) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token offendingToken = (Token) offendingSymbol;

            // 获取前一个非隐藏token
            Token prevToken = getPreviousNonHiddenToken(tokenStream, offendingToken);
            if (prevToken != null && "IDENTIFIER".equals(vocabulary.getSymbolicName(prevToken.getType()))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 专门检测缺少左圆括号的情况
     */
    private boolean isMissingLeftParenthesis(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                             IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!(offendingSymbol instanceof Token)) return false;

        Token token = (Token) offendingSymbol;
        String tokenType = vocabulary.getSymbolicName(token.getType());

        // 情况1：右括号后期望类型说明符
        if ("RPAREN".equals(tokenType) && expectsTypeSpecifier(expected, vocabulary)) {
            return isPrecededByIdentifier(recognizer, token, vocabulary);
        }

        // 情况2：类型说明符后期望类型说明符，且不在结构体定义中
        if (isTypeSpecifier(tokenType) && expectsTypeSpecifier(expected, vocabulary)) {
            return isPrecededByIdentifier(recognizer, token, vocabulary) &&
                    !isInStructDefinitionContext(recognizer, token);
        }

        return false;
    }

    /**
     * 专门检测缺少左方括号的情况
     */
    private boolean isMissingLeftBracket(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                         IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!(offendingSymbol instanceof Token)) return false;

        Token token = (Token) offendingSymbol;
        String tokenType = vocabulary.getSymbolicName(token.getType());

        // 如果当前token是右方括号
        if ("RBRACK".equals(tokenType)) {
            // 情况1：局部变量声明 - 期望的token是赋值或分号
            if (expectsAssignOrSemi(expected, vocabulary)) {
                return isPrecededByIdentifier(recognizer, token, vocabulary);
            }

            // 情况2：全局变量声明 - 期望的token是类型说明符
            if (expectsTypeSpecifier(expected, vocabulary)) {
                return isPrecededByIdentifier(recognizer, token, vocabulary);
            }

            // 情况3：数组访问 - 期望的token是各种操作符或分号
            if (expectsOperatorOrSemi(expected, vocabulary)) {
                return isPrecededByIdentifier(recognizer, token, vocabulary);
            }
        }

        // 新增情况4：多维数组声明 - 当前token是数字，期望赋值或分号，前一个token是右方括号
        if ("Number".equals(tokenType) && expectsAssignOrSemi(expected, vocabulary)) {
            return isPrecededByRightBracket(recognizer, token, vocabulary);
        }

        // 新增情况5：多维数组声明 - 当前token是标识符，期望赋值或分号，前一个token是右方括号
        if ("Identifier".equals(tokenType) && expectsAssignOrSemi(expected, vocabulary)) {
            return isPrecededByRightBracket(recognizer, token, vocabulary);
        }

        return false;
    }

    /**
     * 检查是否前一个token是右方括号
     */
    private boolean isPrecededByRightBracket(Recognizer<?, ?> recognizer, Token token, Vocabulary vocabulary) {
        if (recognizer instanceof SplcParser &&
                ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token prevToken = getPreviousNonHiddenToken(tokenStream, token);

            return prevToken != null && "RBRACK".equals(vocabulary.getSymbolicName(prevToken.getType()));
        }
        return false;
    }

    /**
     * 检查是否期望操作符或分号
     */
    private boolean expectsOperatorOrSemi(IntervalSet expected, Vocabulary vocabulary) {
        for (int expectedType : expected.toArray()) {
            String expectedName = vocabulary.getSymbolicName(expectedType);
            if (isOperator(expectedName) || "SEMI".equals(expectedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是操作符
     */
    private boolean isOperator(String tokenType) {
        return "PLUS".equals(tokenType) || "MINUS".equals(tokenType) ||
                "STAR".equals(tokenType) || "DIV".equals(tokenType) ||
                "ASSIGN".equals(tokenType) || "LT".equals(tokenType) ||
                "LE".equals(tokenType) || "GT".equals(tokenType) ||
                "GE".equals(tokenType) || "EQ".equals(tokenType) ||
                "NEQ".equals(tokenType) || "AND".equals(tokenType) ||
                "OR".equals(tokenType) || "COMMA".equals(tokenType) ||
                "RPAREN".equals(tokenType) || "RBRACK".equals(tokenType) ||
                "RBRACE".equals(tokenType);
    }

    /**
     * 专门检测缺少左大括号的情况
     */
    private boolean isMissingLeftBrace(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                       IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!(offendingSymbol instanceof Token)) return false;

        Token token = (Token) offendingSymbol;
        String tokenType = vocabulary.getSymbolicName(token.getType());

        if (isTypeSpecifier(tokenType) && expectsTypeSpecifier(expected, vocabulary)) {
            return isInStructDefinitionContext(recognizer, token);
        }

        return false;
    }

    // ==================== 辅助检测方法 ====================

    /**
     * 检查是否期望类型说明符
     */
    private boolean expectsTypeSpecifier(IntervalSet expected, Vocabulary vocabulary) {
        for (int expectedType : expected.toArray()) {
            String expectedName = vocabulary.getSymbolicName(expectedType);
            if (isTypeSpecifier(expectedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否期望赋值或分号
     */
    private boolean expectsAssignOrSemi(IntervalSet expected, Vocabulary vocabulary) {
        for (int expectedType : expected.toArray()) {
            String expectedName = vocabulary.getSymbolicName(expectedType);
            if ("ASSIGN".equals(expectedName) || "SEMI".equals(expectedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是类型说明符
     */
    private boolean isTypeSpecifier(String tokenType) {
        return "INT".equals(tokenType) || "CHAR".equals(tokenType) || "STRUCT".equals(tokenType);
    }

    /**
     * 检查是否前一个token是标识符
     */
    private boolean isPrecededByIdentifier(Recognizer<?, ?> recognizer, Token token, Vocabulary vocabulary) {
        if (recognizer instanceof SplcParser &&
                ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token prevToken = getPreviousNonHiddenToken(tokenStream, token);

            return prevToken != null && "Identifier".equals(vocabulary.getSymbolicName(prevToken.getType()));
        }
        return false;
    }

    /**
     * 获取前一个非隐藏token
     */
    private Token getPreviousNonHiddenToken(CommonTokenStream tokenStream, Token currentToken) {
        for (int i = currentToken.getTokenIndex() - 1; i >= 0; i--) {
            Token token = tokenStream.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                return token;
            }
        }
        return null;
    }

    /**
     * 检查是否在结构体定义上下文中
     */
    private boolean isInStructDefinitionContext(Recognizer<?, ?> recognizer, Token currentToken) {
        if (recognizer instanceof SplcParser &&
                ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token prevToken = getPreviousNonHiddenToken(tokenStream, currentToken);

            if (prevToken != null) {
                String prevTokenType = recognizer.getVocabulary().getSymbolicName(prevToken.getType());

                // 如果前一个token是STRUCT，则是在结构体定义中
                if ("STRUCT".equals(prevTokenType)) {
                    return true;
                }

                // 如果前一个token是标识符，检查再前一个token是否是STRUCT
                if ("Identifier".equals(prevTokenType)) {
                    Token prevPrevToken = getPreviousNonHiddenToken(tokenStream, prevToken);
                    return prevPrevToken != null && "STRUCT".equals(recognizer.getVocabulary().getSymbolicName(prevPrevToken.getType()));
                }
            }
        }
        return false;
    }

    // ==================== EOF错误处理 ====================

    private void handleEOFError(Recognizer<?, ?> recognizer, Token eofToken, String msg, RecognitionException e) {
        if (recognizer instanceof SplcParser &&
                ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token lastToken = findLastNonHiddenToken(tokenStream);

            if (lastToken != null) {
                String lastTokenName = recognizer.getVocabulary().getSymbolicName(lastToken.getType());
                String missingSymbol = inferMissingSymbolFromContext(lastTokenName, lastToken.getText());
                int reportLine = Math.max(0, lastToken.getLine() - 1);

                if (missingSymbol != null) {
                    MissingSymbolError error = new MissingSymbolError(missingSymbol, reportLine);
                    this.grader.getWriter().println(error);
                } else {
                    MissingSymbolError error = new MissingSymbolError("RBRACE", reportLine);
                    this.grader.getWriter().println(error);
                }
            }
        }
        throw new ParseCancellationException();
    }

    /**
     * 查找最后一个非隐藏token
     */
    private Token findLastNonHiddenToken(CommonTokenStream tokenStream) {
        List<Token> tokens = tokenStream.getTokens();
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Token token = tokens.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                return token;
            }
        }
        return null;
    }

    private String inferMissingSymbolFromContext(String lastTokenType, String lastTokenText) {
        if ("LBRACE".equals(lastTokenType)) {
            return "RBRACE";
        } else if ("LPAREN".equals(lastTokenType)) {
            return "RPAREN";
        } else if ("main".equals(lastTokenText) || "IDENTIFIER".equals(lastTokenType)) {
            return "LPAREN";
        }
        return null;
    }

    /**
     * 专门检测函数调用缺少左括号的情况
     */
    private boolean isMissingFunctionCallParenthesis(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                                     IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!(offendingSymbol instanceof Token)) return false;

        Token token = (Token) offendingSymbol;
        String tokenType = vocabulary.getSymbolicName(token.getType());

        // 检查是否期望分号，但当前token是数字或标识符（函数参数）
        boolean expectsSemi = false;
        for (int expectedType : expected.toArray()) {
            String expectedName = vocabulary.getSymbolicName(expectedType);
            if ("SEMI".equals(expectedName)) {
                expectsSemi = true;
                break;
            }
        }

        if (expectsSemi && ("Number".equals(tokenType) || "Identifier".equals(tokenType))) {
            // 检查前一个token是否是标识符（函数名）
            if (recognizer instanceof SplcParser &&
                    ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream) {

                CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
                Token prevToken = getPreviousNonHiddenToken(tokenStream, token);

                if (prevToken != null && "Identifier".equals(vocabulary.getSymbolicName(prevToken.getType()))) {
                    // 检查是否在赋值表达式或表达式语句中
                    return isInExpressionContext(recognizer, token);
                }
            }
        }

        return false;
    }

    /**
     * 检查是否在表达式上下文中
     */
    private boolean isInExpressionContext(Recognizer<?, ?> recognizer, Token currentToken) {
        if (recognizer instanceof SplcParser &&
                ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();

            // 检查前面是否有赋值操作符或其他表达式上下文指示符
            for (int i = currentToken.getTokenIndex() - 1; i >= 0; i--) {
                Token t = tokenStream.get(i);
                if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                    String tokenType = recognizer.getVocabulary().getSymbolicName(t.getType());

                    // 如果遇到赋值操作符、逗号、左括号等，说明在表达式上下文中
                    if ("ASSIGN".equals(tokenType) || "COMMA".equals(tokenType) ||
                            "LPAREN".equals(tokenType) || "LBRACK".equals(tokenType) ||
                            "PLUS".equals(tokenType) || "MINUS".equals(tokenType) ||
                            "STAR".equals(tokenType) || "DIV".equals(tokenType) ||
                            "RETURN".equals(tokenType)) {
                        return true;
                    }

                    // 如果遇到分号、右大括号等，说明不在表达式上下文中
                    if ("SEMI".equals(tokenType) || "RBRACE".equals(tokenType)) {
                        return false;
                    }
                }
            }
        }

        // 默认情况下，假设在表达式上下文中
        return true;
    }
}
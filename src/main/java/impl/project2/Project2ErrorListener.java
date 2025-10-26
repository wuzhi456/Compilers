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
        Vocabulary vocabulary = recognizer.getVocabulary();

        // 打印调试信息
        printDebugInfo(recognizer, offendingSymbol, line, charPositionInLine, msg, vocabulary);

        // 特殊处理EOF错误
        if (offendingSymbol instanceof Token && ((Token) offendingSymbol).getType() == Token.EOF) {
            handleEOFError(recognizer, (Token) offendingSymbol, msg, e);
            return;
        }

        String tokenName = extractMissingSymbol(recognizer, msg, offendingSymbol, expected, vocabulary);
        int reportLine = calculateReportLine(line, offendingSymbol, parser);

        if (tokenName != null) {
            MissingSymbolError missingSymbol = new MissingSymbolError(tokenName, reportLine);
            this.grader.getWriter().println(missingSymbol);
            throw new ParseCancellationException();
        }
    }

    /**
     * 提取缺失的符号
     */
    private String extractMissingSymbol(Recognizer<?, ?> recognizer, String msg, Object offendingSymbol,
                                        IntervalSet expected, Vocabulary vocabulary) {
        if (!(offendingSymbol instanceof Token)) {
            return null;
        }

        Token token = (Token) offendingSymbol;
        String tokenText = token.getText();
        String tokenType = vocabulary.getSymbolicName(token.getType());

        System.err.println("DEBUG- Token type: " + tokenType + ", Text: '" + tokenText + "'");
        System.err.println("DEBUG- Expected: " + expected.toString(vocabulary));

        if (msg.contains("no viable alternative") && isInArrayDeclarationContext(recognizer, token, vocabulary)) {
            return "RBRACK";
        }

        // 新增：检测结构体缺少右大括号的情况 - 专门针对 struct A { int a; int main() {} 这种模式
        String missingStructRBrace = detectMissingStructRBrace(recognizer, token, expected, vocabulary, msg);
        if (missingStructRBrace != null) {
            return missingStructRBrace;
        }

        // 新增：检测结构体内部数组声明缺少分号的情况
        if (isMissingSemicolonInStructArray(recognizer, token, expected, vocabulary, msg)) {
            return "SEMI";
        }

        if (msg.contains("no viable alternative")
                && isMissingLeftBracketIn2DArray(recognizer, token, vocabulary)) {
            return "LBRACK";
        }

        // 如果在结构体定义内部，错误为 no viable alternative，且当前期望是类型说明符，
        // 并且当前 token 为类型说明符，而前一个非隐藏 token 是 RBRACK 或 标识符，
        // 则认定为上一成员声明缺少分号，返回 "SEMI"。
        if (msg.contains("no viable alternative")
                && expectsTypeSpecifier(expected, vocabulary)
                && isTypeSpecifier(tokenType)
                && isInStructDefinition(recognizer, token)) {
            CommonTokenStream ts = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token prev = getPreviousNonHiddenToken(ts, token);
            if (prev != null) {
                String prevType = vocabulary.getSymbolicName(prev.getType());
                if ("RBRACK".equals(prevType) || isIdentifierToken(prevType)) {
                    return "SEMI";
                }
            }
        }

        // 与上面逻辑相同，但用更稳健的结构体体内判断（不依赖具体标识符词法名）
        if (msg.contains("no viable alternative")
                && expectsTypeSpecifier(expected, vocabulary)
                && isTypeSpecifier(tokenType)
                && isInStructDefinitionLoose(recognizer, token, vocabulary)) {
            CommonTokenStream ts = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token prev = getPreviousNonHiddenToken(ts, token);
            if (prev != null) {
                String prevType = vocabulary.getSymbolicName(prev.getType());
                if ("RBRACK".equals(prevType) || isIdentifierToken(prevType)) {
                    return "SEMI";
                }
            }
        }

        // 优先检查结构体括号缺失错误
        String structBracketError = detectStructBracketError(recognizer, token, expected, vocabulary, msg);
        if (structBracketError != null) {
            return structBracketError;
        }

        // 优先检查结构体内部缺少分号的情况（标识符结尾）
        if (isMissingSemicolonInStructBody(recognizer, token, expected, vocabulary, msg)) {
            return "SEMI";
        }

        // [新增 - 兜底：RBRACE 跟在 标识符/右方括号 后 且 expected 是类型] -> 缺少分号
        // 该规则不依赖“结构体体内”的判断，避免 isInStructDefinition* 被分号早退误判。
        if ("RBRACE".equals(tokenType)
                && msg.contains("no viable alternative")
                && expectsTypeSpecifier(expected, vocabulary)
                && recognizer instanceof SplcParser
                && ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream) {
            CommonTokenStream ts = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token prev = getPreviousNonHiddenToken(ts, token);
            if (prev != null) {
                String prevType = vocabulary.getSymbolicName(prev.getType());
                if ("RBRACK".equals(prevType) || isIdentifierToken(prevType)) {
                    return "SEMI";
                }
            }
        }

        // 1. 优先检查是否期望分号
        if (expectsSemi(expected, vocabulary)) {
            // 如果是右大括号但期望分号，应该报告分号缺失
            if ("RBRACE".equals(tokenType) && msg.contains("expecting {'=', ';'}")) {
                return "SEMI";
            }

            // 检查是否在二维数组声明中缺少左方括号
            if (isMissingLeftBracketIn2DArray(recognizer, token, vocabulary)) {
                return "LBRACK";
            }

            // 其他期望分号的情况
            return "SEMI";
        }

        // 2. 检查结构体定义后缺少分号
        if (msg.contains("no viable alternative") && isTypeSpecifier(tokenType)) {
            Token prevToken = getPreviousNonHiddenToken(
                    (CommonTokenStream) ((SplcParser) recognizer).getInputStream(), token);

            if (prevToken != null && "RBRACE".equals(vocabulary.getSymbolicName(prevToken.getType()))) {
                // 在右大括号后直接遇到类型说明符，说明结构体定义缺少分号
                return "SEMI";
            }
        }

        // 3. 直接检查不匹配的右括号
        if ((msg.contains("mismatched input '" + tokenText + "'") ||
                msg.contains("extraneous input '" + tokenText + "'")) &&
                isRightBracket(tokenText)) {
            // 但如果期望的是分号，应该优先返回分号
            if (expectsSemi(expected, vocabulary)) {
                return "SEMI";
            }
            return getCorrespondingLeftBracket(tokenText);
        }

        // 4. 检查缺少左括号的情况（注意：此前已对 RBRACE 缺分号做了兜底，避免误判为缺 LBRACE）
        String bracket = extractMissingBracket(recognizer, msg, token, expected, vocabulary);
        if (bracket != null) {
            return bracket;
        }

        // 5. 检查结构体内部缺少分号（早期通用逻辑）
        if (isMissingSemicolonInStruct(recognizer, token, expected, vocabulary, msg)) {
            return "SEMI";
        }

        // 6. 检查函数调用缺少左括号
        if (isMissingFunctionCallParenthesis(recognizer, token, expected, vocabulary, msg)) {
            return "LPAREN";
        }

        // 7. 默认情况：使用期望token中的第一个
        return findPrioritizedToken(expected, vocabulary);
    }

    /**
     * 检测结构体内部数组声明缺少分号的情况
     */
    private boolean isMissingSemicolonInStructArray(Recognizer<?, ?> recognizer, Token token,
                                                    IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!(recognizer instanceof SplcParser) ||
                !(((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream)) {
            return false;
        }

        // 检查是否在结构体内部遇到右大括号，但期望类型说明符
        if ("RBRACE".equals(vocabulary.getSymbolicName(token.getType())) &&
                msg.contains("no viable alternative") &&
                expectsTypeSpecifier(expected, vocabulary)) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token prevToken = getPreviousNonHiddenToken(tokenStream, token);

            // 如果前一个token是右方括号，说明是数组声明缺少分号
            if (prevToken != null && "RBRACK".equals(vocabulary.getSymbolicName(prevToken.getType()))) {
                // 进一步确认是在结构体定义内部
                return isInStructDefinition(recognizer, token);
            }
        }

        return false;
    }

    /**
     * 专门检测结构体缺少右大括号的情况 - 针对 struct A { int a; int main() {} 这种模式
     */
    private String detectMissingStructRBrace(Recognizer<?, ?> recognizer, Token currentToken,
                                             IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!(recognizer instanceof SplcParser) ||
                !(((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream)) {
            return null;
        }

        CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
        String currentTokenType = vocabulary.getSymbolicName(currentToken.getType());

        // 检查错误模式：在结构体内部遇到了函数定义的开始
        if (msg.contains("no viable alternative") &&
                expectsTypeSpecifier(expected, vocabulary) &&
                "LPAREN".equals(currentTokenType)) {

            // 向前查找，看是否在结构体定义内部
            Token prevToken = getPreviousNonHiddenToken(tokenStream, currentToken);
            if (prevToken != null && "Identifier".equals(vocabulary.getSymbolicName(prevToken.getType()))) {
                Token typeToken = getPreviousNonHiddenToken(tokenStream, prevToken);
                if (typeToken != null && isTypeSpecifier(vocabulary.getSymbolicName(typeToken.getType()))) {
                    // 检查这个类型说明符是否在结构体内部
                    if (isTokenInStructBody(tokenStream, typeToken, vocabulary)) {
                        return "RBRACE";
                    }
                }
            }
        }

        return null;
    }

    /**
     * 检查token是否在结构体定义体内
     */
    private boolean isTokenInStructBody(CommonTokenStream tokenStream, Token token, Vocabulary vocabulary) {
        int braceCount = 0;

        for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
            Token t = tokenStream.get(i);
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                String tokenType = vocabulary.getSymbolicName(t.getType());

                if ("RBRACE".equals(tokenType)) {
                    braceCount++;
                } else if ("LBRACE".equals(tokenType)) {
                    braceCount--;
                    if (braceCount < 0) {
                        // 找到了未匹配的左大括号
                        // 检查前面是否有STRUCT
                        for (int j = i - 1; j >= 0; j--) {
                            Token prev = tokenStream.get(j);
                            if (prev.getChannel() == Token.DEFAULT_CHANNEL) {
                                String prevType = vocabulary.getSymbolicName(prev.getType());
                                if ("STRUCT".equals(prevType)) {
                                    return true;
                                } else if ("Identifier".equals(prevType)) {
                                    continue;
                                } else {
                                    break;
                                }
                            }
                        }
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 检测结构体括号缺失错误
     */
    private String detectStructBracketError(Recognizer<?, ?> recognizer, Token currentToken,
                                            IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!(recognizer instanceof SplcParser) ||
                !(((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream)) {
            return null;
        }

        CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
        String currentTokenType = vocabulary.getSymbolicName(currentToken.getType());

        // 情况1: 结构体定义缺少左大括号
        // 模式: struct Identifier 后直接跟类型说明符
        if (msg.contains("no viable alternative") && isTypeSpecifier(currentTokenType)) {
            Token prevToken = getPreviousNonHiddenToken(tokenStream, currentToken);
            if (prevToken != null && "Identifier".equals(vocabulary.getSymbolicName(prevToken.getType()))) {
                Token structToken = getPreviousNonHiddenToken(tokenStream, prevToken);
                if (structToken != null && "STRUCT".equals(vocabulary.getSymbolicName(structToken.getType()))) {
                    // struct Identifier 后直接遇到类型说明符，说明缺少左大括号
                    return "LBRACE";
                }
            }
        }

        // 情况2: 结构体定义缺少右大括号
        // 模式: 在结构体内部遇到函数定义或其他全局定义
        if (msg.contains("no viable alternative") &&
                ("LPAREN".equals(currentTokenType) || isTypeSpecifier(currentTokenType))) {

            // 检查是否在未闭合的结构体定义中
            if (isInUnclosedStructDefinition(recognizer, currentToken, vocabulary)) {
                return "RBRACE";
            }
        }

        return null;
    }

    /**
     * 检测结构体内部缺少分号的情况
     */
    private boolean isMissingSemicolonInStructBody(Recognizer<?, ?> recognizer, Token token,
                                                   IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!(recognizer instanceof SplcParser) ||
                !(((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream)) {
            return false;
        }

        // 检查是否在结构体内部遇到右大括号，但期望类型说明符
        if ("RBRACE".equals(vocabulary.getSymbolicName(token.getType())) &&
                msg.contains("no viable alternative") &&
                expectsTypeSpecifier(expected, vocabulary)) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token prevToken = getPreviousNonHiddenToken(tokenStream, token);

            // 如果前一个token是标识符，说明是结构体成员声明缺少分号
            if (prevToken != null && "Identifier".equals(vocabulary.getSymbolicName(prevToken.getType()))) {
                // 进一步确认是在结构体定义内部
                return isInStructDefinition(recognizer, token);
            }
        }

        return false;
    }

    /**
     * 检测是否在数组声明上下文中缺少右方括号
     */
    private boolean isInArrayDeclarationContext(Recognizer<?, ?> recognizer, Token currentToken, Vocabulary vocabulary) {
        if (!(recognizer instanceof SplcParser) ||
                !(((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream)) {
            return false;
        }

        CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();

        // 检查当前token是否是分号，且前面有左方括号和数字/标识符
        if ("SEMI".equals(vocabulary.getSymbolicName(currentToken.getType()))) {
            Token prevToken = getPreviousNonHiddenToken(tokenStream, currentToken);
            if (prevToken != null) {
                String prevTokenType = vocabulary.getSymbolicName(prevToken.getType());

                // 如果前一个token是数字或标识符，继续向前找左方括号
                if ("Number".equals(prevTokenType) || "Identifier".equals(prevTokenType)) {
                    Token lBracketToken = getPreviousNonHiddenToken(tokenStream, prevToken);
                    if (lBracketToken != null && "LBRACK".equals(vocabulary.getSymbolicName(lBracketToken.getType()))) {
                        // 找到了左方括号，说明这是数组声明缺少右方括号
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 检测是否在二维数组声明中缺少左方括号
     */
    private boolean isMissingLeftBracketIn2DArray(Recognizer<?, ?> recognizer, Token currentToken, Vocabulary vocabulary) {
        if (!(recognizer instanceof SplcParser) ||
                !(((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream)) {
            return false;
        }

        CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();

        // 检查当前token是否是数字，且前面有右方括号
        if ("Number".equals(vocabulary.getSymbolicName(currentToken.getType()))) {
            Token prevToken = getPreviousNonHiddenToken(tokenStream, currentToken);
            if (prevToken != null && "RBRACK".equals(vocabulary.getSymbolicName(prevToken.getType()))) {
                // 在右方括号后直接遇到数字，这是二维数组声明缺少左方括号
                return true;
            }
        }

        return false;
    }

    /**
     * 提取缺失的括号 - 简化逻辑
     */
    private String extractMissingBracket(Recognizer<?, ?> recognizer, String msg, Token token,
                                         IntervalSet expected, Vocabulary vocabulary) {
        String tokenType = vocabulary.getSymbolicName(token.getType());

        // 情况1: no viable alternative 错误
        if (msg.contains("no viable alternative")) {
            // 右括号后期望类型说明符
            if (isRightBracketType(tokenType) && expectsTypeSpecifier(expected, vocabulary)) {
                return getCorrespondingLeftBracketFromType(tokenType);
            }

            // 类型说明符前有标识符 - 简化这个逻辑
            if (isPrecededByIdentifier(recognizer, token, vocabulary) &&
                    isTypeSpecifier(tokenType)) {
                // 更准确地判断是否在结构体定义中
                if (isInStructDefinition(recognizer, token)) {
                    return "LBRACE";
                }
                return "LPAREN";
            }
        }

        return null;
    }

    // ==================== 新增和改进的方法 ====================

    /**
     * 更准确地判断是否在结构体定义中
     */
    private boolean isInStructDefinition(Recognizer<?, ?> recognizer, Token currentToken) {
        CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
        Token token = currentToken;

        // 向前查找，看是否有未闭合的结构体定义
        int braceCount = 0;
        for (int i = currentToken.getTokenIndex() - 1; i >= 0; i--) {
            Token t = tokenStream.get(i);
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                String tType = recognizer.getVocabulary().getSymbolicName(t.getType());

                if ("RBRACE".equals(tType)) {
                    braceCount++;
                } else if ("LBRACE".equals(tType)) {
                    braceCount--;
                    if (braceCount < 0) {
                        // 找到了未匹配的左大括号，检查前面是否有STRUCT
                        for (int j = i - 1; j >= 0; j--) {
                            Token prevToken = tokenStream.get(j);
                            if (prevToken.getChannel() == Token.DEFAULT_CHANNEL) {
                                String prevType = recognizer.getVocabulary().getSymbolicName(prevToken.getType());
                                if ("STRUCT".equals(prevType)) {
                                    return true;
                                } else if (!"Identifier".equals(prevType)) {
                                    break;
                                }
                            }
                        }
                        return false;
                    }
                } else if ("SEMI".equals(tType) && braceCount == 0) {
                    // 遇到了分号且没有未闭合的大括号，说明不在结构体定义中
                    return false;
                }
            }
        }
        return false;
    }

    // 更宽松且对标识符词法名不敏感的“结构体体内”判断
    private boolean isInStructDefinitionLoose(Recognizer<?, ?> recognizer, Token currentToken, Vocabulary vocabulary) {
        if (!(recognizer instanceof SplcParser) ||
                !(((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream)) {
            return false;
        }
        CommonTokenStream ts = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();

        int brace = 0;
        for (int i = currentToken.getTokenIndex() - 1; i >= 0; i--) {
            Token t = ts.get(i);
            if (t.getChannel() != Token.DEFAULT_CHANNEL) continue;

            String tType = vocabulary.getSymbolicName(t.getType());
            if ("RBRACE".equals(tType)) {
                brace++;
            } else if ("LBRACE".equals(tType)) {
                brace--;
                if (brace < 0) {
                    // 找到了一个在当前点仍未闭合的左大括号，向前查找是否属于 struct 定义
                    for (int j = i - 1; j >= 0; j--) {
                        Token p = ts.get(j);
                        if (p.getChannel() != Token.DEFAULT_CHANNEL) continue;
                        String pType = vocabulary.getSymbolicName(p.getType());

                        if ("STRUCT".equals(pType)) {
                            return true; // 确认是 struct 定义体
                        }
                        // 遇到这些符号可以认为不是在有效的 struct 定义体之前
                        if ("SEMI".equals(pType) || "RBRACE".equals(pType)) {
                            return false;
                        }
                        // 允许跳过标识符、类型说明符等
                    }
                    return false;
                }
            } else if ("SEMI".equals(tType) && brace == 0) {
                // 顶层遇到分号，说明在此之前没有未闭合的 '{' 可以归属到 struct
                return false;
            }
        }
        return false;
    }

    /**
     * 检测是否在未闭合的结构体定义中
     */
    private boolean isInUnclosedStructDefinition(Recognizer<?, ?> recognizer, Token currentToken, Vocabulary vocabulary) {
        if (!(recognizer instanceof SplcParser) ||
                !(((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream)) {
            return false;
        }

        CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();

        // 向前查找未匹配的左大括号
        int braceCount = 0;
        for (int i = currentToken.getTokenIndex() - 1; i >= 0; i--) {
            Token token = tokenStream.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                String tokenType = vocabulary.getSymbolicName(token.getType());

                if ("RBRACE".equals(tokenType)) {
                    braceCount++;
                } else if ("LBRACE".equals(tokenType)) {
                    braceCount--;
                    if (braceCount < 0) {
                        // 找到了未匹配的左大括号，检查前面是否有STRUCT
                        for (int j = i - 1; j >= 0; j--) {
                            Token prevToken = tokenStream.get(j);
                            if (prevToken.getChannel() == Token.DEFAULT_CHANNEL) {
                                String prevTokenType = vocabulary.getSymbolicName(prevToken.getType());
                                if ("STRUCT".equals(prevTokenType)) {
                                    return true; // 是结构体定义的左大括号
                                } else if ("Identifier".equals(prevTokenType)) {
                                    continue; // 继续向前找
                                } else {
                                    break; // 不是结构体定义
                                }
                            }
                        }
                        return false;
                    }
                } else if ("SEMI".equals(tokenType) && braceCount == 0) {
                    // 遇到了分号且没有未闭合的大括号，说明不在结构体定义中
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 报告行号计算
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

    // ==================== 保留的核心工具方法 ====================

    private void printDebugInfo(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String msg, Vocabulary vocabulary) {
        System.err.println("Expected tokens: " + recognizer.getVocabulary().getDisplayName(recognizer.getVocabulary().getMaxTokenType()));
        System.err.println("Offending symbol: " + offendingSymbol);
        System.err.println("Error at line " + line + ":" + charPositionInLine);

        if (offendingSymbol instanceof Token) {
            Token token = (Token) offendingSymbol;
            System.err.println("Offending token type: " + vocabulary.getSymbolicName(token.getType()));
            System.err.println("Offending token text: '" + token.getText() + "'");
            System.err.println("Offending token line: " + token.getLine());
        }
    }

    private String findPrioritizedToken(IntervalSet expected, Vocabulary vocabulary) {
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

        for (int tokenType : expected.toArray()) {
            if (tokenType != Token.EOF) {
                return vocabulary.getSymbolicName(tokenType);
            }
        }

        return null;
    }

    // ==================== 辅助检测方法 ====================

    private boolean isRightBracket(String tokenText) {
        return "]".equals(tokenText) || ")".equals(tokenText) || "}".equals(tokenText);
    }

    private boolean isRightBracketType(String tokenType) {
        return "RBRACK".equals(tokenType) || "RPAREN".equals(tokenType) || "RBRACE".equals(tokenType);
    }

    private String getCorrespondingLeftBracket(String rightBracket) {
        switch (rightBracket) {
            case "]": return "LBRACK";
            case ")": return "LPAREN";
            case "}": return "LBRACE";
            default: return null;
        }
    }

    private String getCorrespondingLeftBracketFromType(String rightBracketType) {
        switch (rightBracketType) {
            case "RBRACK": return "LBRACK";
            case "RPAREN": return "LPAREN";
            case "RBRACE": return "LBRACE";
            default: return null;
        }
    }

    // ==================== 保留的核心检测方法 ====================

    private boolean isMissingSemicolonInStruct(Recognizer<?, ?> recognizer, Token token,
                                               IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!msg.contains("no viable alternative")) return false;
        if (!expectsTypeSpecifier(expected, vocabulary)) return false;

        Token prevToken = getPreviousNonHiddenToken(
                (CommonTokenStream) ((SplcParser) recognizer).getInputStream(), token);

        return prevToken != null && "IDENTIFIER".equals(vocabulary.getSymbolicName(prevToken.getType()));
    }

    private boolean isMissingFunctionCallParenthesis(Recognizer<?, ?> recognizer, Token token,
                                                     IntervalSet expected, Vocabulary vocabulary, String msg) {
        if (!expectsSemi(expected, vocabulary)) return false;
        if (!"Number".equals(vocabulary.getSymbolicName(token.getType())) &&
                !"Identifier".equals(vocabulary.getSymbolicName(token.getType()))) return false;

        Token prevToken = getPreviousNonHiddenToken(
                (CommonTokenStream) ((SplcParser) recognizer).getInputStream(), token);

        return prevToken != null && "Identifier".equals(vocabulary.getSymbolicName(prevToken.getType())) &&
                isInExpressionContext(recognizer, token);
    }

    private boolean isInExpressionContext(Recognizer<?, ?> recognizer, Token currentToken) {
        // 简化的表达式上下文检查
        if (recognizer instanceof SplcParser &&
                ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();

            for (int i = currentToken.getTokenIndex() - 1; i >= 0; i--) {
                Token t = tokenStream.get(i);
                if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                    String tokenType = recognizer.getVocabulary().getSymbolicName(t.getType());
                    if ("ASSIGN".equals(tokenType) || "COMMA".equals(tokenType) || "RETURN".equals(tokenType)) {
                        return true;
                    }
                    if ("SEMI".equals(tokenType) || "RBRACE".equals(tokenType)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ==================== 基础辅助方法 ====================

    private boolean expectsTypeSpecifier(IntervalSet expected, Vocabulary vocabulary) {
        for (int expectedType : expected.toArray()) {
            String expectedName = vocabulary.getSymbolicName(expectedType);
            if (isTypeSpecifier(expectedName)) return true;
        }
        return false;
    }

    private boolean expectsSemi(IntervalSet expected, Vocabulary vocabulary) {
        for (int expectedType : expected.toArray()) {
            String expectedName = vocabulary.getSymbolicName(expectedType);
            if ("SEMI".equals(expectedName)) return true;
        }
        return false;
    }

    private boolean isTypeSpecifier(String tokenType) {
        return "INT".equals(tokenType) || "CHAR".equals(tokenType) || "STRUCT".equals(tokenType);
    }

    private boolean isPrecededByIdentifier(Recognizer<?, ?> recognizer, Token token, Vocabulary vocabulary) {
        Token prevToken = getPreviousNonHiddenToken(
                (CommonTokenStream) ((SplcParser) recognizer).getInputStream(), token);

        return prevToken != null && "Identifier".equals(vocabulary.getSymbolicName(prevToken.getType()));
    }

    private Token getPreviousNonHiddenToken(CommonTokenStream tokenStream, Token currentToken) {
        for (int i = currentToken.getTokenIndex() - 1; i >= 0; i--) {
            Token token = tokenStream.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                return token;
            }
        }
        return null;
    }

    // ==================== EOF错误处理 ====================

    private void handleEOFError(Recognizer<?, ?> recognizer, Token eofToken, String msg, RecognitionException e) {
        if (recognizer instanceof SplcParser &&
                ((SplcParser) recognizer).getInputStream() instanceof CommonTokenStream) {

            CommonTokenStream tokenStream = (CommonTokenStream) ((SplcParser) recognizer).getInputStream();
            Token lastToken = findLastNonHiddenToken(tokenStream);

            if (lastToken != null) {
                String missingSymbol = inferMissingSymbolFromEOF(tokenStream, lastToken, recognizer.getVocabulary());
                int reportLine = Math.max(0, lastToken.getLine() - 1);

                MissingSymbolError error = new MissingSymbolError(
                        missingSymbol != null ? missingSymbol : "RBRACE", reportLine);
                this.grader.getWriter().println(error);
            }
        }
        throw new ParseCancellationException();
    }

    private String inferMissingSymbolFromEOF(CommonTokenStream tokenStream, Token lastToken, Vocabulary vocabulary) {
        String lastTokenType = vocabulary.getSymbolicName(lastToken.getType());

        // 情况1：最后一个token是右大括号，检查是否是结构体定义
        if ("RBRACE".equals(lastTokenType)) {
            if (isStructDefinitionEnd(tokenStream, lastToken, vocabulary)) {
                return "SEMI"; // 结构体定义缺少分号
            }
            return "RBRACE"; // 其他情况缺少右大括号
        }

        // 情况2：最后一个token是标识符，检查是否是结构体声明
        if ("Identifier".equals(lastTokenType)) {
            Token prevToken = getPreviousNonHiddenToken(tokenStream, lastToken);
            if (prevToken != null && "STRUCT".equals(vocabulary.getSymbolicName(prevToken.getType()))) {
                return "SEMI"; // 结构体声明缺少分号
            }
        }

        // 情况3：使用原来的推断逻辑
        return inferMissingSymbolFromContext(lastTokenType, lastToken.getText());
    }

    private boolean isStructDefinitionEnd(CommonTokenStream tokenStream, Token rbraceToken, Vocabulary vocabulary) {
        // 向前查找匹配的左大括号
        int braceCount = 1;
        for (int i = rbraceToken.getTokenIndex() - 1; i >= 0; i--) {
            Token token = tokenStream.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                String tokenType = vocabulary.getSymbolicName(token.getType());

                if ("RBRACE".equals(tokenType)) {
                    braceCount++;
                } else if ("LBRACE".equals(tokenType)) {
                    braceCount--;
                    if (braceCount == 0) {
                        // 找到了匹配的左大括号，检查前面是否有STRUCT
                        for (int j = i - 1; j >= 0; j--) {
                            Token prevToken = tokenStream.get(j);
                            if (prevToken.getChannel() == Token.DEFAULT_CHANNEL) {
                                String prevTokenType = vocabulary.getSymbolicName(prevToken.getType());
                                if ("STRUCT".equals(prevTokenType)) {
                                    return true; // 是结构体定义
                                } else if ("Identifier".equals(prevTokenType) || "INT".equals(prevTokenType) ||
                                        "CHAR".equals(prevTokenType) || "RBRACE".equals(prevTokenType)) {
                                    // 继续向前查找
                                    continue;
                                } else {
                                    break; // 不是结构体定义
                                }
                            }
                        }
                        return false;
                    }
                }
            }
        }
        return false;
    }

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
        if ("LBRACE".equals(lastTokenType)) return "RBRACE";
        if ("LPAREN".equals(lastTokenType)) return "RPAREN";
        if ("main".equals(lastTokenText) || "IDENTIFIER".equals(lastTokenType)) return "LPAREN";
        return null;
    }

    // 兼容不同词法名的标识符判断
    private boolean isIdentifierToken(String tokenName) {
        return "IDENTIFIER".equals(tokenName) || "Identifier".equals(tokenName) || "ID".equals(tokenName);
    }
}
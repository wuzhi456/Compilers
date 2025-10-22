package impl.project2;

import generated.Splc.SplcBaseVisitor;
import generated.Splc.SplcParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class ConstExprVisitor extends SplcBaseVisitor<Integer> {
    @Override
    public Integer visitExpression(SplcParser.ExpressionContext ctx) {
        int n = ctx.getChildCount();

        // 1) 单个终结符：Number / Char -> 常量；Identifier -> 非 constexpr
        if (n == 1 && ctx.getChild(0) instanceof TerminalNode) {
            TerminalNode tn = (TerminalNode) ctx.getChild(0);
            Token t = tn.getSymbol();
            switch (t.getType()) {
                case SplcParser.Number:
                    try {
                        return Integer.parseInt(t.getText());
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                case SplcParser.Char:
                    return parseCharLiteral(t.getText());
                default:
                    return null;
            }
        }


        // 2) 括号
        if (ctx.LPAREN() != null && ctx.RPAREN() != null && ctx.expression().size() == 1) {
            return visit(ctx.expression(0));
        }

        // 3) 前缀一元：+ / -
        if (ctx.getChildCount() == 2) {
            String op0 = ctx.getChild(0).getText();
            // 前缀形式：op expr
            if ("+".equals(op0) || "-".equals(op0)) {
                Integer v = visit(ctx.expression(0));
                if (v == null) return null;
                return "+".equals(op0) ? +v : -v;
            }
            // 后缀形式（expr ++ / expr --）或其他前缀：不是 constexpr
            return null;
        }

        // 4) 二元算术：+ - * / %
        if (ctx.getChildCount() == 3) {
            ParseTree left = ctx.getChild(0);
            ParseTree op = ctx.getChild(1);
            ParseTree right = ctx.getChild(2);

            if (left instanceof SplcParser.ExpressionContext
                    && right instanceof SplcParser.ExpressionContext) {
                String opText = op.getText();
                Integer lv = visit((SplcParser.ExpressionContext) left);
                Integer rv = visit((SplcParser.ExpressionContext) right);
                if (lv == null || rv == null) return null;

                switch (opText) {
                    case "+": return lv + rv;
                    case "-": return lv - rv;
                    case "*": return lv * rv;
                    case "/": return rv == 0 ? null : lv / rv;
                    case "%": return rv == 0 ? null : lv % rv;
                    default:  return null;
                }
            }
        }
        return null;
    }

    private Integer parseCharLiteral(String text) {
        // 支持: 'a' '\n' '\t' '\'' '\\' '\0'
        if (text == null || text.length() < 3) return null;
        if (text.charAt(0) != '\'' || text.charAt(text.length() - 1) != '\'') return null;

        if (text.charAt(1) != '\\') {
            return (int) text.charAt(1);
        } else {
            if (text.length() != 4) return null;
            switch (text.charAt(2)) {
                case 'n': return (int) '\n';
                case 't': return (int) '\t';
                case '0': return 0;
                case '\'': return (int) '\'';
                case '\\': return (int) '\\';
                default: return null;
            }
        }
    }
}

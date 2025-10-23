package impl.project2;

import framework.project2.Grader;
import framework.project2.MissingSymbolError;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.misc.IntervalSet;
import generated.Splc.SplcParser;

public class Project2ErrorListener extends BaseErrorListener {
    private final Grader grader;
    public Project2ErrorListener(Grader grader) {
        this.grader = grader;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        // TODO: extract information
        SplcParser parser = (SplcParser) recognizer;
        IntervalSet expected = parser.getExpectedTokens();
        String tokenName = null;

        for (int tokenType : expected.toArray()) {
            if (tokenType == Token.EOF) {
                continue;
            }
            tokenName = recognizer.getVocabulary().getSymbolicName(tokenType);
            break;
        }

        if (tokenName == null) {
            return;
        }

//        int reportLine = Math.max(0, line - 1);
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

        MissingSymbolError missingSymbol = new MissingSymbolError(tokenName, reportLine);

        this.grader.getWriter().println(missingSymbol);
        throw new ParseCancellationException();
    }
}

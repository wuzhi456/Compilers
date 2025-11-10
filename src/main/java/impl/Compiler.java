package impl;

import framework.AbstractCompiler;
import framework.AbstractGrader;
import framework.project3.Project3SemanticError;
import generated.Splc.SplcBaseVisitor;
import generated.Splc.SplcLexer;
import generated.Splc.SplcParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;

public class Compiler extends AbstractCompiler {
    public Compiler(AbstractGrader grader) {
        super(grader);
    }

    @Override
    public void start() throws IOException {
        CharStream input = CharStreams.fromStream(this.grader.getSourceStream());
        SplcLexer lexer = new SplcLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SplcParser parser = new SplcParser(tokens);

        // TODO: XXX
        SplcParser.ProgramContext program = parser.program();

        new SplcBaseVisitor<Void>() {
            // These are merely examples to show how to create and report a Semantic Error.
            // The Alternative name (ExprID, VarDecBase, etc.) used here may not be the same as yours.
            // So it's fine that this code won't compile. You are free to delete all of these code.
            @Override
            public Void visitExprID(SplcParser.ExprIDContext ctx) {
                var ident = ctx.Identifier();
                grader.reportSemanticError(Project3SemanticError.undeclaredUse(ident));
                return null;
            }

            @Override
            public Void visitVarDecBase(SplcParser.VarDecBaseContext ctx) {
                var ident = ctx.Identifier();
                grader.reportSemanticError(Project3SemanticError.redefinition(ident));
                return null;
            }
        }.visit(program);

        System.out.println("wow");

        grader.print("Variables:\n");

        grader.print("\n");

        grader.print("Functions:\n");
    }
}

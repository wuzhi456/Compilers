package impl;

import framework.AbstractCompiler;
import framework.AbstractGrader;
import framework.lang.Type;
import framework.project3.Project3SemanticError;
import generated.Splc.SplcBaseVisitor;
import generated.Splc.SplcLexer;
import generated.Splc.SplcParser;
import impl.symbol.Symbol;
import impl.symbol.SymbolTable;
import impl.types.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.util.*;

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

        SplcParser.ProgramContext program = parser.program();

        // Phase 1: Semantic analysis and error checking
        SemanticAnalyzer analyzer = new SemanticAnalyzer(grader);
        analyzer.visit(program);

        // If we get here, no semantic errors were found
        // Phase 2: Print global variables and functions
        grader.print("Variables:\n");
        for (Symbol symbol : analyzer.getGlobalVariables()) {
            grader.print(symbol.getName() + ": " + symbol.getType().fullPrint() + "\n");
        }
        grader.print("\n");
        grader.print("Functions:\n");
        for (Symbol symbol : analyzer.getGlobalFunctions()) {
            grader.print(symbol.getName() + ": " + symbol.getType().prettyPrint() + "\n");
        }
    }

    private static class SemanticAnalyzer extends SplcBaseVisitor<Type> {
        private final AbstractGrader grader;
        private final SymbolTable symbolTable;
        private final List<Symbol> globalVariables = new ArrayList<>();
        private final List<Symbol> globalFunctions = new ArrayList<>();

        public SemanticAnalyzer(AbstractGrader grader) {
            this.grader = grader;
            this.symbolTable = new SymbolTable();
        }

        public List<Symbol> getGlobalVariables() {
            return globalVariables;
        }

        public List<Symbol> getGlobalFunctions() {
            return globalFunctions;
        }

        @Override
        public Type visitProgram(SplcParser.ProgramContext ctx) {
            for (SplcParser.GlobalDefContext globalDef : ctx.globalDef()) {
                visitGlobalDef(globalDef);
            }
            return null;
        }

        @Override
        public Type visitGlobalDef(SplcParser.GlobalDefContext ctx) {
            Type specType = visitSpecifier(ctx.specifier());
            
            if (ctx.LBRACE() != null) {
                // Function definition: specifier Identifier LPAREN funcArgs RPAREN LBRACE statement* RBRACE
                TerminalNode funcName = ctx.Identifier();
                List<Type> paramTypes = new ArrayList<>();
                if (ctx.funcArgs().specifier() != null && !ctx.funcArgs().specifier().isEmpty()) {
                    for (int i = 0; i < ctx.funcArgs().specifier().size(); i++) {
                        Type paramSpecType = visitSpecifier(ctx.funcArgs().specifier(i));
                        Type paramType = buildTypeFromVarDec(ctx.funcArgs().varDec(i), paramSpecType);
                        paramTypes.add(paramType);
                    }
                }
                
                FunctionType funcType = new FunctionType(specType, paramTypes);
                
                // Check if function already exists in current scope
                Symbol existing = symbolTable.lookupOther(funcName.getText());
                if (existing != null && existing.getScopeId() == symbolTable.getCurrentScopeId()) {
                    // If it's already a definition (FUNCTION), that's a redefinition error
                    if (existing.getKind() == Symbol.Kind.FUNCTION) {
                        grader.reportSemanticError(Project3SemanticError.redefinition(funcName));
                    }
                    // If it's a declaration (FUNCTION_DECL), we can define it - update it
                    symbolTable.updateOther(funcName.getText(), funcType, Symbol.Kind.FUNCTION);
                } else {
                    // No existing symbol, add it
                    symbolTable.addOther(funcName.getText(), funcType, Symbol.Kind.FUNCTION);
                }
                
                // Track in global functions list (but check if already there from declaration)
                boolean alreadyInList = false;
                for (Symbol s : globalFunctions) {
                    if (s.getName().equals(funcName.getText())) {
                        alreadyInList = true;
                        break;
                    }
                }
                if (!alreadyInList) {
                    globalFunctions.add(new Symbol(funcName.getText(), funcType, Symbol.Kind.FUNCTION, 0));
                }
                
                // Enter function scope for parameters and body
                symbolTable.enterScope();
                
                // Add parameters to the function scope
                if (ctx.funcArgs().specifier() != null && !ctx.funcArgs().specifier().isEmpty()) {
                    for (int i = 0; i < ctx.funcArgs().specifier().size(); i++) {
                        Type paramSpecType = visitSpecifier(ctx.funcArgs().specifier(i));
                        String paramName = extractIdentifierFromVarDec(ctx.funcArgs().varDec(i));
                        Type paramType = buildTypeFromVarDec(ctx.funcArgs().varDec(i), paramSpecType);
                        
                        // Check for incomplete type
                        if (!isCompleteType(paramType)) {
                            grader.reportSemanticError(Project3SemanticError.definitionIncomplete(
                                getIdentifierNode(ctx.funcArgs().varDec(i))));
                        }
                        
                        symbolTable.addOther(paramName, paramType, Symbol.Kind.VARIABLE);
                    }
                }
                
                // Visit function body
                for (SplcParser.StatementContext stmt : ctx.statement()) {
                    visit(stmt);
                }
                
                symbolTable.exitScope();
                
            } else if (ctx.varDec() != null) {
                // Global variable definition: specifier varDec (ASSIGN expression)? SEMI
                String varName = extractIdentifierFromVarDec(ctx.varDec());
                Type varType = buildTypeFromVarDec(ctx.varDec(), specType);
                
                // Check for incomplete type
                if (!isCompleteType(varType)) {
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(
                        getIdentifierNode(ctx.varDec())));
                }
                
                // Check redefinition
                if (symbolTable.existsInCurrentScopeOther(varName)) {
                    grader.reportSemanticError(Project3SemanticError.redefinition(getIdentifierNode(ctx.varDec())));
                }
                
                symbolTable.addOther(varName, varType, Symbol.Kind.VARIABLE);
                globalVariables.add(new Symbol(varName, varType, Symbol.Kind.VARIABLE, 0));
                
            } else if (ctx.Identifier() != null && ctx.funcArgs() != null) {
                // Function declaration: specifier Identifier LPAREN funcArgs RPAREN SEMI
                TerminalNode funcName = ctx.Identifier();
                List<Type> paramTypes = new ArrayList<>();
                if (ctx.funcArgs().specifier() != null && !ctx.funcArgs().specifier().isEmpty()) {
                    for (int i = 0; i < ctx.funcArgs().specifier().size(); i++) {
                        Type paramSpecType = visitSpecifier(ctx.funcArgs().specifier(i));
                        Type paramType = buildTypeFromVarDec(ctx.funcArgs().varDec(i), paramSpecType);
                        paramTypes.add(paramType);
                    }
                }
                
                FunctionType funcType = new FunctionType(specType, paramTypes);
                
                // Check redeclaration - can't declare a function if already declared/defined
                Symbol existing = symbolTable.lookupOther(funcName.getText());
                if (existing != null && existing.getScopeId() == symbolTable.getCurrentScopeId()) {
                    grader.reportSemanticError(Project3SemanticError.redeclaration(funcName));
                }
                
                symbolTable.addOther(funcName.getText(), funcType, Symbol.Kind.FUNCTION_DECL);
                globalFunctions.add(new Symbol(funcName.getText(), funcType, Symbol.Kind.FUNCTION_DECL, 0));
            }
            // else: global struct declaration (specifier SEMI) - already handled in visitSpecifier
            
            return null;
        }

        @Override
        public Type visitSpecifier(SplcParser.SpecifierContext ctx) {
            if (ctx.INT() != null) {
                return new BasicType(BasicType.Kind.INT);
            } else if (ctx.CHAR() != null) {
                return new BasicType(BasicType.Kind.CHAR);
            } else if (ctx.STRUCT() != null) {
                String tagName = ctx.Identifier().getText();
                
                if (ctx.LBRACE() != null) {
                    // Complete struct: struct Identifier { ... }
                    
                    // Parse members first
                    List<StructType.Member> members = new ArrayList<>();
                    Set<String> memberNames = new HashSet<>();
                    
                    for (int i = 0; i < ctx.specifier().size(); i++) {
                        Type memberSpecType = visitSpecifier(ctx.specifier(i));
                        String memberName = extractIdentifierFromVarDec(ctx.varDec(i));
                        Type memberType = buildTypeFromVarDec(ctx.varDec(i), memberSpecType);
                        
                        // Check for incomplete member type
                        if (!isCompleteType(memberType)) {
                            grader.reportSemanticError(Project3SemanticError.memberIncomplete(
                                getIdentifierNode(ctx.varDec(i))));
                        }
                        
                        // Check for duplicate member names
                        if (memberNames.contains(memberName)) {
                            grader.reportSemanticError(Project3SemanticError.memberDuplicate(
                                getIdentifierNode(ctx.varDec(i))));
                        }
                        memberNames.add(memberName);
                        
                        members.add(new StructType.Member(memberType, memberName));
                    }
                    
                    // Now check if this tag was already declared as complete in this scope
                    Symbol existingTag = symbolTable.lookupTag(tagName);
                    if (existingTag != null && existingTag.getScopeId() == symbolTable.getCurrentScopeId()) {
                        StructType existingStructType = (StructType) existingTag.getType();
                        if (existingStructType.isComplete()) {
                            grader.reportSemanticError(Project3SemanticError.redeclaration(ctx.Identifier()));
                        }
                    }
                    
                    StructType structType = new StructType(tagName, members);
                    
                    // Update or add the tag
                    if (existingTag != null && existingTag.getScopeId() == symbolTable.getCurrentScopeId()) {
                        symbolTable.updateTag(tagName, structType);
                    } else {
                        symbolTable.addTag(tagName, structType);
                    }
                    return structType;
                    
                } else {
                    // Incomplete struct reference: struct Identifier
                    Symbol existingTag = symbolTable.lookupTag(tagName);
                    if (existingTag != null) {
                        // Return the existing type (could be complete or incomplete)
                        return existingTag.getType();
                    } else {
                        // Declare new incomplete struct type
                        StructType structType = new StructType(tagName);
                        symbolTable.addTag(tagName, structType);
                        return structType;
                    }
                }
            }
            return null;
        }

        @Override
        public Type visitBracket(SplcParser.BracketContext ctx) {
            // Block statement: LBRACE statement* RBRACE
            symbolTable.enterScope();
            for (SplcParser.StatementContext stmt : ctx.statement()) {
                visit(stmt);
            }
            symbolTable.exitScope();
            return null;
        }

        @Override
        public Type visitVarDecStmt(SplcParser.VarDecStmtContext ctx) {
            // Local variable declaration: specifier varDec (ASSIGN expression)? SEMI
            Type specType = visitSpecifier(ctx.specifier());
            String varName = extractIdentifierFromVarDec(ctx.varDec());
            Type varType = buildTypeFromVarDec(ctx.varDec(), specType);
            
            // Check for incomplete type
            if (!isCompleteType(varType)) {
                grader.reportSemanticError(Project3SemanticError.definitionIncomplete(
                    getIdentifierNode(ctx.varDec())));
            }
            
            // Check redefinition in current scope
            if (symbolTable.existsInCurrentScopeOther(varName)) {
                grader.reportSemanticError(Project3SemanticError.redefinition(
                    getIdentifierNode(ctx.varDec())));
            }
            
            symbolTable.addOther(varName, varType, Symbol.Kind.VARIABLE);
            
            // Visit initialization expression if present
            if (ctx.expression() != null) {
                visitExpression(ctx.expression());
            }
            return null;
        }

        @Override
        public Type visitIfStmt(SplcParser.IfStmtContext ctx) {
            visitExpression(ctx.expression());
            visit(ctx.statement(0));
            if (ctx.statement().size() > 1) {
                visit(ctx.statement(1));
            }
            return null;
        }

        @Override
        public Type visitWhileStmt(SplcParser.WhileStmtContext ctx) {
            visitExpression(ctx.expression());
            visit(ctx.statement());
            return null;
        }

        @Override
        public Type visitReturnStmt(SplcParser.ReturnStmtContext ctx) {
            visitExpression(ctx.expression());
            return null;
        }

        @Override
        public Type visitExprStmt(SplcParser.ExprStmtContext ctx) {
            visitExpression(ctx.expression());
            return null;
        }

        @Override
        public Type visitExpression(SplcParser.ExpressionContext ctx) {
            // First, check if this expression has an identifier that needs to be looked up
            if (ctx.Identifier() != null) {
                String name = ctx.Identifier().getText();
                Symbol symbol = symbolTable.lookupOther(name);
                if (symbol == null) {
                    grader.reportSemanticError(Project3SemanticError.undeclaredUse(ctx.Identifier()));
                }
            }
            
            // Visit all sub-expressions
            for (int i = 0; i < ctx.getChildCount(); i++) {
                if (ctx.getChild(i) instanceof SplcParser.ExpressionContext) {
                    visitExpression((SplcParser.ExpressionContext) ctx.getChild(i));
                }
            }
            
            return null;
        }

        // Helper methods
        
        private String extractIdentifierFromVarDec(SplcParser.VarDecContext ctx) {
            if (ctx.Identifier() != null) {
                return ctx.Identifier().getText();
            } else if (ctx.varDec() != null) {
                return extractIdentifierFromVarDec(ctx.varDec());
            }
            return null;
        }

        private TerminalNode getIdentifierNode(SplcParser.VarDecContext ctx) {
            if (ctx.Identifier() != null) {
                return ctx.Identifier();
            } else if (ctx.varDec() != null) {
                return getIdentifierNode(ctx.varDec());
            }
            return null;
        }

        private Type buildTypeFromVarDec(SplcParser.VarDecContext ctx, Type baseType) {
            if (ctx.Identifier() != null) {
                // Base case: just the identifier
                return baseType;
            } else if (ctx.LPAREN() != null && ctx.varDec() != null) {
                // Parenthesized: LPAREN varDec RPAREN
                return buildTypeFromVarDec(ctx.varDec(), baseType);
            } else if (ctx.LBRACK() != null) {
                // Array: varDec LBRACK Number RBRACK
                // Build array type from baseType, then pass it down
                int arraySize = Integer.parseInt(ctx.Number().getText());
                Type arrayType = new ArrayType(baseType, arraySize);
                return buildTypeFromVarDec(ctx.varDec(), arrayType);
            } else if (ctx.STAR() != null) {
                // Pointer: STAR varDec
                // Build pointer type from baseType, then pass it down
                Type pointerType = new PointerType(baseType);
                return buildTypeFromVarDec(ctx.varDec(), pointerType);
            }
            return baseType;
        }

        private boolean isCompleteType(Type type) {
            if (type instanceof BasicType) {
                return true;
            } else if (type instanceof PointerType) {
                return true; // Pointers are always complete
            } else if (type instanceof ArrayType) {
                // Array is complete if element type is complete
                return isCompleteType(((ArrayType) type).getElementType());
            } else if (type instanceof StructType) {
                return ((StructType) type).isComplete();
            } else if (type instanceof FunctionType) {
                return true;
            }
            return false;
        }
    }
}

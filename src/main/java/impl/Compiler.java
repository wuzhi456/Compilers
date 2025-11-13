package impl;

import framework.AbstractCompiler;
import framework.AbstractGrader;
import framework.lang.Type;
import framework.project3.Project3SemanticError;
import generated.Splc.SplcBaseVisitor;
import generated.Splc.SplcLexer;
import generated.Splc.SplcParser;
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

        parser.removeErrorListeners();
        lexer.removeErrorListeners();

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

    // ===== Type System Inner Classes =====

    private static class BasicType implements Type {
        public enum Kind {
            INT, CHAR
        }

        private final Kind kind;

        public BasicType(Kind kind) {
            this.kind = kind;
        }

        public Kind getKind() {
            return kind;
        }

        @Override
        public String prettyPrint() {
            return kind == Kind.INT ? "int" : "char";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BasicType)) return false;
            BasicType other = (BasicType) obj;
            return kind == other.kind;
        }

        @Override
        public int hashCode() {
            return kind.hashCode();
        }
    }

    private static class ArrayType implements Type {
        private final Type elementType;
        private final int length;

        public ArrayType(Type elementType, int length) {
            this.elementType = elementType;
            this.length = length;
        }

        public Type getElementType() {
            return elementType;
        }

        public int getLength() {
            return length;
        }

        @Override
        public String prettyPrint() {
            return elementType.prettyPrint() + "[" + length + "]";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ArrayType)) return false;
            ArrayType other = (ArrayType) obj;
            return length == other.length && elementType.equals(other.elementType);
        }

        @Override
        public int hashCode() {
            return elementType.hashCode() * 31 + length;
        }
    }

    private static class PointerType implements Type {
        private final Type referencedType;

        public PointerType(Type referencedType) {
            this.referencedType = referencedType;
        }

        public Type getReferencedType() {
            return referencedType;
        }

        @Override
        public String prettyPrint() {
            return referencedType.prettyPrint() + "*";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PointerType)) return false;
            PointerType other = (PointerType) obj;
            return referencedType.equals(other.referencedType);
        }

        @Override
        public int hashCode() {
            return referencedType.hashCode() * 17;
        }
    }

    private static class FunctionType implements Type {
        private final Type returnType;
        private final List<Type> parameterTypes;

        public FunctionType(Type returnType, List<Type> parameterTypes) {
            this.returnType = returnType;
            this.parameterTypes = new ArrayList<>(parameterTypes);
        }

        public Type getReturnType() {
            return returnType;
        }

        public List<Type> getParameterTypes() {
            return parameterTypes;
        }

        @Override
        public String prettyPrint() {
            StringBuilder sb = new StringBuilder();
            sb.append(returnType.prettyPrint()).append("(");
            for (int i = 0; i < parameterTypes.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(parameterTypes.get(i).prettyPrint());
            }
            sb.append(")");
            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof FunctionType)) return false;
            FunctionType other = (FunctionType) obj;
            return returnType.equals(other.returnType) &&
                    parameterTypes.equals(other.parameterTypes);
        }

        @Override
        public int hashCode() {
            return returnType.hashCode() * 31 + parameterTypes.hashCode();
        }
    }

    private static class StructType implements Type {
        private final String tag;
        private final List<Member> members;
        private final boolean isComplete;

        private boolean isInDef;

        public static class Member {
            public final Type type;
            public final String name;

            public Member(Type type, String name) {
                this.type = type;
                this.name = name;
            }
        }

        // Constructor for incomplete struct
        public StructType(String tag, boolean isInDef) {
            this.tag = tag;
            this.members = null;
            this.isComplete = false;
            this.isInDef = isInDef;
        }

        // Constructor for complete struct
        public StructType(String tag, List<Member> members) {
            this.tag = tag;
            this.members = new ArrayList<>(members);
            this.isComplete = true;
            this.isInDef = false;
        }

        public String getTag() {
            return tag;
        }

        public List<Member> getMembers() {
            return members;
        }

        public boolean isComplete() {
            return isComplete;
        }

        public boolean isInDef(){
            return isInDef;
        }

        @Override
        public String prettyPrint() {
            return "struct " + tag;
        }

        @Override
        public String fullPrint() {
            if (!isComplete || members == null) {
                return prettyPrint();
            }
            StringBuilder sb = new StringBuilder("struct ");
            sb.append(tag).append("{");
            for (Member member : members) {
                sb.append(member.type.prettyPrint()).append(" ").append(member.name).append(";");
            }
            sb.append("}");
            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StructType)) return false;
            StructType other = (StructType) obj;
            // Two struct types are the same if they have the same tag
            // and same completeness (in same scope context, but we check that elsewhere)
            return tag.equals(other.tag);
        }

        @Override
        public int hashCode() {
            return tag.hashCode();
        }
    }

    // ===== Symbol Table Inner Classes =====

    private static class Symbol {
        public enum Kind {
            VARIABLE,    // Variables and function names (in "other" namespace)
            FUNCTION,    // Actually same as variable, but we track separately
            FUNCTION_DECL, // Function declaration (no body)
            STRUCT_TAG,  // Structure tags
            STRUCT_MEMBER // Structure members (each struct has its own namespace)
        }

        private final String name;
        private final Type type;
        private final Kind kind;
        private final int scopeId;  // For tracking which scope this belongs to

        public Symbol(String name, Type type, Kind kind, int scopeId) {
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.scopeId = scopeId;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public Kind getKind() {
            return kind;
        }

        public int getScopeId() {
            return scopeId;
        }
    }

    private static class SymbolTable {
        // Scopes for different namespaces
        private final Deque<Map<String, Symbol>> otherScopes = new ArrayDeque<>();  // variables, functions
        private final Deque<Map<String, Symbol>> tagScopes = new ArrayDeque<>();    // struct tags

        private int scopeIdCounter = 0;
        private int currentScopeId = 0;

        public SymbolTable() {
            // Start with file scope
            enterScope();
        }

        public void enterScope() {
            otherScopes.push(new LinkedHashMap<>());
            tagScopes.push(new LinkedHashMap<>());
            currentScopeId = scopeIdCounter++;
        }

        public void exitScope() {
            if (otherScopes.size() > 1) {
                otherScopes.pop();
                tagScopes.pop();
            }
        }

        public int getCurrentScopeId() {
            return currentScopeId;
        }

        // Add symbol to "other" namespace (variables, functions)
        public boolean addOther(String name, Type type, Symbol.Kind kind) {
            Map<String, Symbol> currentScope = otherScopes.peek();
            if (currentScope.containsKey(name)) {
                return false; // Already exists in current scope
            }
            currentScope.put(name, new Symbol(name, type, kind, currentScopeId));
            return true;
        }

        // Update existing symbol in current scope (for converting function decl to definition)
        public void updateOther(String name, Type type, Symbol.Kind kind) {
            Map<String, Symbol> currentScope = otherScopes.peek();
            currentScope.put(name, new Symbol(name, type, kind, currentScopeId));
        }

        // Add symbol to "tag" namespace (struct tags)
        public boolean addTag(String name, Type type) {
            Map<String, Symbol> currentScope = tagScopes.peek();
            if (currentScope.containsKey(name)) {
                return false; // Already exists in current scope
            }
            currentScope.put(name, new Symbol(name, type, Symbol.Kind.STRUCT_TAG, currentScopeId));
            return true;
        }

        // Update existing tag in current scope (for completing incomplete structs)
        public void updateTag(String name, Type type) {
            Map<String, Symbol> currentScope = tagScopes.peek();
            currentScope.put(name, new Symbol(name, type, Symbol.Kind.STRUCT_TAG, currentScopeId));
        }

        // Lookup in "other" namespace
        public Symbol lookupOther(String name) {
            for (Map<String, Symbol> scope : otherScopes) {
                if (scope.containsKey(name)) {
                    return scope.get(name);
                }
            }
            return null;
        }

        // Lookup in "tag" namespace
        public Symbol lookupTag(String name) {
            for (Map<String, Symbol> scope : tagScopes) {
                if (scope.containsKey(name)) {
                    return scope.get(name);
                }
            }
            return null;

        }

        public Symbol lookupForDef(String name) {
            for (Map<String, Symbol> scope : tagScopes) {
                if (scope.containsKey(name)) {
                    Symbol sym = scope.get(name);
                    if (sym.getType() instanceof StructType) {
                        StructType st = (StructType)sym.getType();
                        if (st.isInDef() || st.isComplete()) {
                            return sym;
                        }
                    }
                }
            }
            return null;
        }

        // Check if symbol exists in current scope only (for redefinition check)
        public boolean existsInCurrentScopeOther(String name) {
            return otherScopes.peek().containsKey(name);
        }

        public boolean existsInCurrentScopeTag(String name) {
            return tagScopes.peek().containsKey(name);
        }

        // Get all symbols in file scope (for printing at the end)
        public List<Symbol> getFileScopeOthers() {
            if (otherScopes.isEmpty()) return Collections.emptyList();

            // File scope is at the bottom of the stack
            Map<String, Symbol> fileScope = null;
            for (Map<String, Symbol> scope : otherScopes) {
                fileScope = scope;
            }
            return fileScope != null ? new ArrayList<>(fileScope.values()) : Collections.emptyList();
        }
    }

    // ===== Semantic Analyzer =====

    private static class SemanticAnalyzer extends SplcBaseVisitor<Type> {
        private final AbstractGrader grader;
        private final SymbolTable symbolTable;
        private final List<Symbol> globalVariables = new ArrayList<>();
        private final List<Symbol> globalFunctions = new ArrayList<>();

        private final Map<Symbol, TerminalNode> incompleteGlobals = new LinkedHashMap<>();


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

        private List<String> getTagNamesFromType(Type type) {
            List<String> result = new ArrayList<>();
            if (type instanceof StructType) {
                result.add(((StructType) type).getTag());
            } else if (type instanceof PointerType) {
                result.addAll(getTagNamesFromType(((PointerType) type).getReferencedType()));
            } else if (type instanceof ArrayType) {
                result.addAll(getTagNamesFromType(((ArrayType) type).getElementType()));
            }
            return result;
        }

        @Override
        public Type visitProgram(SplcParser.ProgramContext ctx) {
            for (SplcParser.GlobalDefContext globalDef : ctx.globalDef()) {
                visitGlobalDef(globalDef);
            }
            for (Symbol s : incompleteGlobals.keySet()) {
                Type type = s.getType();
                List<String> tags = getTagNamesFromType(type);
                boolean allStructComplete = true;
                for (String tagName : tags) {
                    Symbol tagSym = symbolTable.lookupTag(tagName);
                    Type tagType = (tagSym != null) ? tagSym.getType() : null;
                    if (tagType instanceof StructType) {
                        if (!((StructType) tagType).isComplete()) {
                            allStructComplete = false;
                            break;
                        }
                    } else {
                        allStructComplete = false;
                        break;
                    }
                }
                if (!allStructComplete) {
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(incompleteGlobals.get(s)));
                }
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
//                Symbol existing = symbolTable.lookupOther(funcName.getText());
//                if (existing != null && existing.getScopeId() == symbolTable.getCurrentScopeId()) {
//                    // If it's already a definition (FUNCTION), that's a redefinition error
//                    if (existing.getKind() == Symbol.Kind.FUNCTION) {
//                        grader.reportSemanticError(Project3SemanticError.redefinition(funcName));
//                    }
//                    // If it's a declaration (FUNCTION_DECL), we can define it - update it
//                    symbolTable.updateOther(funcName.getText(), funcType, Symbol.Kind.FUNCTION);
//                } else {
//                    // No existing symbol, add it
//                    symbolTable.addOther(funcName.getText(), funcType, Symbol.Kind.FUNCTION);
//                }
                // 查找file scope所有符号
                for (Symbol s : symbolTable.getFileScopeOthers()) {
                    if (s.getName().equals(funcName.getText()) && s.getKind() == Symbol.Kind.FUNCTION) {
                        // 已经有同名函数定义，不管scopeId
                        grader.reportSemanticError(Project3SemanticError.redefinition(funcName));
                        break;
                    }
                }
                symbolTable.addOther(funcName.getText(), funcType, Symbol.Kind.FUNCTION);

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
                        // check for duplicate param
                        if (!symbolTable.addOther(paramName, paramType, Symbol.Kind.VARIABLE)) {
                            grader.reportSemanticError(Project3SemanticError.redefinition(getIdentifierNode(ctx.funcArgs().varDec(i))));
                        }

                        // else symbolTable.addOther(paramName, paramType, Symbol.Kind.VARIABLE);
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

                if (!symbolTable.addOther(varName, varType, Symbol.Kind.VARIABLE)) {
                    grader.reportSemanticError(Project3SemanticError.redefinition(getIdentifierNode(ctx.varDec())));
                }
                globalVariables.add(new Symbol(varName, varType, Symbol.Kind.VARIABLE, 0));


                // Check for incomplete type
                if (!isCompleteType(varType)) {
                    // For arrays, the element type must be complete immediately
                    // For direct struct types, we can defer the check per v4 spec
                    if (requiresImmediateCompletenessCheck(varType)) {
                        if (!isCompleteType(varType)) {
                            grader.reportSemanticError(Project3SemanticError.definitionIncomplete(getIdentifierNode(ctx.varDec())));
                        }
                    } else if (!isCompleteType(varType)) {
                        // Defer check for direct struct types
                        incompleteGlobals.put(new Symbol(varName, varType, Symbol.Kind.VARIABLE, 0), getIdentifierNode(ctx.varDec()));
                    }
                }

                // Check redefinition
//                if (symbolTable.existsInCurrentScopeOther(varName)) {
//                    grader.reportSemanticError(Project3SemanticError.redefinition(getIdentifierNode(ctx.varDec())));
//                }
                // 查 file scope，变量/函数/声明同名都不行
//                for (Symbol s : symbolTable.getFileScopeOthers()) {
//                    if (s.getName().equals(varName)) {
//                        grader.reportSemanticError(Project3SemanticError.redefinition(getIdentifierNode(ctx.varDec())));
//                        break;
//                    }
//                }


            } else if (ctx.Identifier() != null && ctx.funcArgs() != null) {
                // Function declaration: specifier Identifier LPAREN funcArgs RPAREN SEMI
                TerminalNode funcName = ctx.Identifier();
                List<Type> paramTypes = new ArrayList<>();
                Set<String> paramNames = new HashSet<>();
                if (ctx.funcArgs().specifier() != null && !ctx.funcArgs().specifier().isEmpty()) {
                    for (int i = 0; i < ctx.funcArgs().specifier().size(); i++) {
                        Type paramSpecType = visitSpecifier(ctx.funcArgs().specifier(i));
                        Type paramType = buildTypeFromVarDec(ctx.funcArgs().varDec(i), paramSpecType);
                        paramTypes.add(paramType);

                        // Check for duplicate parameter names
                        String paramName = extractIdentifierFromVarDec(ctx.funcArgs().varDec(i));
                        if (paramNames.contains(paramName)) {
                            grader.reportSemanticError(Project3SemanticError.redefinition(getIdentifierNode(ctx.funcArgs().varDec(i))));
                        }
                        paramNames.add(paramName);
                    }
                }

                FunctionType funcType = new FunctionType(specType, paramTypes);

                // Check redeclaration - can't declare a function if already declared/defined
                // 查 file scope，是否已经有同名函数
//                for (Symbol s : symbolTable.getFileScopeOthers()) {
//                    if (s.getName().equals(funcName.getText()) &&
//                            (s.getKind() == Symbol.Kind.FUNCTION || s.getKind() == Symbol.Kind.FUNCTION_DECL)) {
//                        grader.reportSemanticError(Project3SemanticError.redeclaration(funcName));
//                        break;
//                    }
//                }
                for (Symbol s : symbolTable.getFileScopeOthers()) {
                    // 只要同名，不管 Kind
                    if (s.getName().equals(funcName.getText())) {
                        grader.reportSemanticError(Project3SemanticError.redeclaration(funcName));
                        break;
                    }
                }

                // 允许第一个声明
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
                    // 完整 struct 定义
                    Symbol existingTag = symbolTable.lookupForDef(tagName);
                    if (existingTag != null) {
                        grader.reportSemanticError(Project3SemanticError.redeclaration(ctx.Identifier()));
                    }
                    // 占位符注册并标记“定义中”
                    symbolTable.enterScope();
                    StructType incompleteStruct = new StructType(tagName, true);
                    symbolTable.addTag(tagName, incompleteStruct);

                    List<StructType.Member> members = new ArrayList<>();
                    Set<String> memberNames = new HashSet<>();
                    for (int i = 0; i < ctx.specifier().size(); i++) {
                        Type memberSpecType = visitSpecifier(ctx.specifier(i));
                        String memberName = extractIdentifierFromVarDec(ctx.varDec(i));
                        Type memberType = buildTypeFromVarDec(ctx.varDec(i), memberSpecType);

                        if (!isCompleteType(memberType)) {
                            grader.reportSemanticError(Project3SemanticError.memberIncomplete(getIdentifierNode(ctx.varDec(i))));
                        }
                        if (memberNames.contains(memberName)) {
                            grader.reportSemanticError(Project3SemanticError.memberDuplicate(getIdentifierNode(ctx.varDec(i))));
                        }
                        memberNames.add(memberName);
                        members.add(new StructType.Member(memberType, memberName));
                    }
                    symbolTable.exitScope();
                    StructType structType = new StructType(tagName, members);
                    symbolTable.updateTag(tagName, structType); // 设置完成定义，内部 isInDef=false
                    return structType;

                } else {
                    // 不完整 struct: 声明或递归引用
                    Symbol existingTag = symbolTable.lookupTag(tagName);
                    if (existingTag != null) {
                        return existingTag.getType();
                    } else {
                        // 注册声明，不在定义体内
                        StructType structType = new StructType(tagName, false);
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

        private boolean requiresImmediateCompletenessCheck(Type type) {
            if (type instanceof ArrayType) {
                // Arrays always require immediate check of element type
                return true;
            } else if (type instanceof PointerType) {
                // Pointers don't require immediate check
                return false;
            }
            // Direct struct types can be deferred for global variables
            return false;
        }
    }
}
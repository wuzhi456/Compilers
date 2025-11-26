package impl;

import framework.AbstractCompiler;
import framework.AbstractGrader;
import framework.lang.Type;
import framework.project3.Project3SemanticError;
import framework.project4.Project4SemanticError;
import framework.project4.Project4Exception;
import generated.Splc.SplcBaseVisitor;
import generated.Splc.SplcLexer;
import generated.Splc.SplcParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
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

        // If we get here, no semantic errors were found (or only Project 4 errors)
        // Phase 2: Print global variables and functions only if no errors
        if (!analyzer.hasSemanticErrors()) {
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

    // Expression info: type + value category
    private static class ExprInfo {
        public final Type type;
        public final boolean isLvalue;

        public ExprInfo(Type type, boolean isLvalue) {
            this.type = type;
            this.isLvalue = isLvalue;
        }
    }

    private static class SemanticAnalyzer extends SplcBaseVisitor<Type> {
        private final AbstractGrader grader;
        private final SymbolTable symbolTable;
        private final List<Symbol> globalVariables = new ArrayList<>();
        private final List<Symbol> globalFunctions = new ArrayList<>();

        private final Map<Symbol, TerminalNode> incompleteGlobals = new LinkedHashMap<>();
        private Type currentFunctionReturnType = null; // Track current function's return type
        private boolean hasSemanticErrors = false; // Track if any semantic errors occurred


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

        public boolean hasSemanticErrors() {
            return hasSemanticErrors;
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
                currentFunctionReturnType = specType; // Track return type for return statement checking

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

            } else if (ctx.varDec() != null && !ctx.varDec().isEmpty()) {
                // Global variable definition: specifier varDec (ASSIGN expression)? (COMMA varDec (ASSIGN expression)?)* SEMI
                List<SplcParser.VarDecContext> varDecs = ctx.varDec();
                List<SplcParser.ExpressionContext> expressions = ctx.expression();
                
                // Map each varDec to its initialization expression (if any)
                // by walking through children in order
                Map<Integer, Integer> varDecToExprIndex = new HashMap<>();
                int currentVarDecIndex = -1;
                int currentExprIndex = 0;
                
                for (int i = 0; i < ctx.getChildCount(); i++) {
                    ParseTree child = ctx.getChild(i);
                    if (child instanceof SplcParser.VarDecContext) {
                        currentVarDecIndex++;
                    } else if (child instanceof TerminalNode) {
                        TerminalNode tn = (TerminalNode) child;
                        if (tn.getSymbol().getType() == SplcLexer.ASSIGN && currentVarDecIndex >= 0) {
                            // The next expression belongs to the current varDec
                            if (currentExprIndex < expressions.size()) {
                                varDecToExprIndex.put(currentVarDecIndex, currentExprIndex);
                                currentExprIndex++;
                            }
                        }
                    }
                }
                
                for (int i = 0; i < varDecs.size(); i++) {
                    SplcParser.VarDecContext varDecCtx = varDecs.get(i);
                    String varName = extractIdentifierFromVarDec(varDecCtx);
                    Type varType = buildTypeFromVarDec(varDecCtx, specType);

                    if (!symbolTable.addOther(varName, varType, Symbol.Kind.VARIABLE)) {
                        grader.reportSemanticError(Project3SemanticError.redefinition(getIdentifierNode(varDecCtx)));
                    }
                    globalVariables.add(new Symbol(varName, varType, Symbol.Kind.VARIABLE, 0));


                    // Check for incomplete type
                    if (!isCompleteType(varType)) {
                        // For arrays, the element type must be complete immediately
                        // For direct struct types, we can defer the check per v4 spec
                        if (requiresImmediateCompletenessCheck(varType)) {
                            grader.reportSemanticError(Project3SemanticError.definitionIncomplete(getIdentifierNode(varDecCtx)));
                        } else {
                            // Defer check for direct struct types
                            incompleteGlobals.put(new Symbol(varName, varType, Symbol.Kind.VARIABLE, 0), getIdentifierNode(varDecCtx));
                        }
                    }
                    
                    // Note: initialization expressions are checked but global variables 
                    // in this grammar don't need type checking for init expressions 
                    // as per Project 4 spec (only local variables need this check)
                }

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
            // Local variable declaration: specifier varDec (ASSIGN expression)? (COMMA varDec (ASSIGN expression)?)* SEMI
            Type specType = visitSpecifier(ctx.specifier());
            
            // Process variable declarations by iterating through children in order
            // The structure is: specifier (varDec (ASSIGN expression)? (COMMA varDec (ASSIGN expression)?)*)+ SEMI
            List<SplcParser.VarDecContext> varDecs = ctx.varDec();
            List<SplcParser.ExpressionContext> expressions = ctx.expression();
            
            // Map each varDec to its initialization expression (if any)
            // by walking through children in order
            Map<Integer, Integer> varDecToExprIndex = new HashMap<>();
            int currentVarDecIndex = -1;
            int currentExprIndex = 0;
            
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof SplcParser.VarDecContext) {
                    currentVarDecIndex++;
                } else if (child instanceof TerminalNode) {
                    TerminalNode tn = (TerminalNode) child;
                    if (tn.getSymbol().getType() == SplcLexer.ASSIGN && currentVarDecIndex >= 0) {
                        // The next expression belongs to the current varDec
                        if (currentExprIndex < expressions.size()) {
                            varDecToExprIndex.put(currentVarDecIndex, currentExprIndex);
                            currentExprIndex++;
                        }
                    }
                }
            }
            
            // Now process each varDec
            for (int i = 0; i < varDecs.size(); i++) {
                SplcParser.VarDecContext varDecCtx = varDecs.get(i);
                String varName = extractIdentifierFromVarDec(varDecCtx);
                Type varType = buildTypeFromVarDec(varDecCtx, specType);

                // Check for incomplete type
                if (!isCompleteType(varType)) {
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(
                            getIdentifierNode(varDecCtx)));
                }

                // Check redefinition in current scope
                if (symbolTable.existsInCurrentScopeOther(varName)) {
                    grader.reportSemanticError(Project3SemanticError.redefinition(
                            getIdentifierNode(varDecCtx)));
                }

                symbolTable.addOther(varName, varType, Symbol.Kind.VARIABLE);

                // Check initialization expression if present
                if (varDecToExprIndex.containsKey(i)) {
                    int exprIndex = varDecToExprIndex.get(i);
                    try {
                        ExprInfo exprInfo = checkExpression(expressions.get(exprIndex));
                        if (!typesEqual(varType, exprInfo.type)) {
                            Project4SemanticError.unexpectedType(expressions.get(exprIndex), exprInfo.type).throwException();
                        }
                    } catch (Project4Exception ex) {
                        hasSemanticErrors = true;
                        grader.reportSemanticError(ex);
                    }
                }
            }
            return null;
        }

        @Override
        public Type visitIfStmt(SplcParser.IfStmtContext ctx) {
            // Check condition type
            try {
                ExprInfo condInfo = checkExpression(ctx.expression());
                // Condition must be integer or pointer
                if (!isIntegerType(condInfo.type) && !isPointerType(condInfo.type)) {
                    Project4SemanticError.unexpectedType(ctx.expression(), condInfo.type).throwException();
                }
            } catch (Project4Exception ex) {
                hasSemanticErrors = true;
                grader.reportSemanticError(ex);
            }

            visit(ctx.statement(0));
            if (ctx.statement().size() > 1) {
                visit(ctx.statement(1));
            }
            return null;
        }

        @Override
        public Type visitWhileStmt(SplcParser.WhileStmtContext ctx) {
            // Check condition type
            try {
                ExprInfo condInfo = checkExpression(ctx.expression());
                // Condition must be integer or pointer
                if (!isIntegerType(condInfo.type) && !isPointerType(condInfo.type)) {
                    Project4SemanticError.unexpectedType(ctx.expression(), condInfo.type).throwException();
                }
            } catch (Project4Exception ex) {
                hasSemanticErrors = true;
                grader.reportSemanticError(ex);
            }

            visit(ctx.statement());
            return null;
        }

        @Override
        public Type visitReturnStmt(SplcParser.ReturnStmtContext ctx) {
            // Check return type
            try {
                ExprInfo exprInfo = checkExpression(ctx.expression());
                if (currentFunctionReturnType != null && !typesEqual(currentFunctionReturnType, exprInfo.type)) {
                    Project4SemanticError.unexpectedType(ctx.expression(), exprInfo.type).throwException();
                }
            } catch (Project4Exception ex) {
                hasSemanticErrors = true;
                grader.reportSemanticError(ex);
            }
            return null;
        }

        @Override
        public Type visitExprStmt(SplcParser.ExprStmtContext ctx) {
            // Just check the expression
            try {
                checkExpression(ctx.expression());
            } catch (Project4Exception ex) {
                hasSemanticErrors = true;
                grader.reportSemanticError(ex);
            }
            return null;
        }

        // ===== Expression Type Checking =====

        private ExprInfo checkExpression(SplcParser.ExpressionContext ctx) {
            // Identifier (but not member access or function call)
            if (ctx.Identifier() != null && ctx.LPAREN() == null && ctx.DOT() == null && ctx.ARROW() == null) {
                String name = ctx.Identifier().getText();
                Symbol symbol = symbolTable.lookupOther(name);
                if (symbol == null) {
                    grader.reportSemanticError(Project3SemanticError.undeclaredUse(ctx.Identifier()));
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), false); // Error recovery
                }
                if (symbol.getKind() == Symbol.Kind.FUNCTION || symbol.getKind() == Symbol.Kind.FUNCTION_DECL) {
                    Project4SemanticError.identifierNotVariable(ctx, name).throwException();
                }
                return new ExprInfo(symbol.getType(), true); // Variables are lvalues
            }

            // Number
            if (ctx.Number() != null) {
                return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
            }

            // Parenthesized expression
            if (ctx.LPAREN() != null && ctx.expression().size() == 1 && ctx.Identifier() == null) {
                ExprInfo inner = checkExpression(ctx.expression(0));
                return new ExprInfo(inner.type, inner.isLvalue);
            }

            // Function call: Identifier LPAREN (expression (COMMA expression)*)? RPAREN
            if (ctx.Identifier() != null && ctx.LPAREN() != null) {
                String funcName = ctx.Identifier().getText();
                Symbol symbol = symbolTable.lookupOther(funcName);
                if (symbol == null) {
                    grader.reportSemanticError(Project3SemanticError.undeclaredUse(ctx.Identifier()));
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
                }
                if (symbol.getKind() != Symbol.Kind.FUNCTION && symbol.getKind() != Symbol.Kind.FUNCTION_DECL) {
                    Project4SemanticError.identifierNotFunction(ctx, funcName).throwException();
                }
                
                FunctionType funcType = (FunctionType) symbol.getType();
                List<Type> paramTypes = funcType.getParameterTypes();
                
                // Get actual arguments
                List<SplcParser.ExpressionContext> args = new ArrayList<>();
                for (int i = 0; i < ctx.expression().size(); i++) {
                    args.add(ctx.expression(i));
                }
                
                // Check parameter count
                if (args.size() != paramTypes.size()) {
                    Project4SemanticError.badParamCount(ctx, paramTypes.size(), args.size()).throwException();
                }
                
                // Check parameter types
                for (int i = 0; i < args.size(); i++) {
                    ExprInfo argInfo = checkExpression(args.get(i));
                    if (!typesEqual(paramTypes.get(i), argInfo.type)) {
                        Project4SemanticError.badParamType(ctx, i + 1).throwException();
                    }
                }
                
                return new ExprInfo(funcType.getReturnType(), false);
            }

            // Array access: expression LBRACK expression RBRACK
            if (ctx.LBRACK() != null) {
                ExprInfo arrayInfo = checkExpression(ctx.expression(0));
                ExprInfo indexInfo = checkExpression(ctx.expression(1));
                
                // Index must be integer
                if (!isIntegerType(indexInfo.type)) {
                    Project4SemanticError.unexpectedType(ctx, indexInfo.type).throwException();
                }
                
                Type elementType;
                if (arrayInfo.type instanceof ArrayType) {
                    // If it's an array, it must be an lvalue
                    if (!arrayInfo.isLvalue) {
                        Project4SemanticError.unexpectedType(ctx, arrayInfo.type).throwException();
                    }
                    elementType = ((ArrayType) arrayInfo.type).getElementType();
                } else if (arrayInfo.type instanceof PointerType) {
                    elementType = ((PointerType) arrayInfo.type).getReferencedType();
                } else {
                    Project4SemanticError.unexpectedType(ctx, arrayInfo.type).throwException();
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), true);
                }
                
                return new ExprInfo(elementType, true); // Array access is lvalue
            }

            // Struct member access: expression DOT Identifier
            if (ctx.DOT() != null) {
                ExprInfo structInfo = checkExpression(ctx.expression(0));
                String memberName = ctx.Identifier().getText();
                
                if (!(structInfo.type instanceof StructType)) {
                    Project4SemanticError.unexpectedType(ctx, structInfo.type).throwException();
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), true);
                }
                
                if (!structInfo.isLvalue) {
                    Project4SemanticError.lvalueRequired(ctx).throwException();
                }
                
                StructType structType = (StructType) structInfo.type;
                if (!structType.isComplete()) {
                    Project4SemanticError.unexpectedType(ctx, structType).throwException();
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), true);
                }
                
                // Find member
                Type memberType = null;
                for (StructType.Member member : structType.getMembers()) {
                    if (member.name.equals(memberName)) {
                        memberType = member.type;
                        break;
                    }
                }
                
                if (memberType == null) {
                    Project4SemanticError.badMember(ctx, structType, memberName).throwException();
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), true);
                }
                
                return new ExprInfo(memberType, true); // Member access is lvalue
            }

            // Struct pointer access: expression ARROW Identifier
            if (ctx.ARROW() != null) {
                ExprInfo ptrInfo = checkExpression(ctx.expression(0));
                String memberName = ctx.Identifier().getText();
                
                if (!(ptrInfo.type instanceof PointerType)) {
                    Project4SemanticError.unexpectedType(ctx, ptrInfo.type).throwException();
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), true);
                }
                
                Type referencedType = ((PointerType) ptrInfo.type).getReferencedType();
                if (!(referencedType instanceof StructType)) {
                    Project4SemanticError.unexpectedType(ctx, ptrInfo.type).throwException();
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), true);
                }
                
                StructType structType = (StructType) referencedType;
                if (!structType.isComplete()) {
                    Project4SemanticError.unexpectedType(ctx, ptrInfo.type).throwException();
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), true);
                }
                
                // Find member
                Type memberType = null;
                for (StructType.Member member : structType.getMembers()) {
                    if (member.name.equals(memberName)) {
                        memberType = member.type;
                        break;
                    }
                }
                
                if (memberType == null) {
                    Project4SemanticError.badMember(ctx, structType, memberName).throwException();
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), true);
                }
                
                return new ExprInfo(memberType, true); // Member access is lvalue
            }

            // Postfix increment/decrement: expression INC/DEC
            if (ctx.INC() != null || ctx.DEC() != null) {
                if (ctx.expression().size() == 1) {
                    ExprInfo operandInfo = checkExpression(ctx.expression(0));
                    
                    if (!operandInfo.isLvalue) {
                        Project4SemanticError.lvalueRequired(ctx).throwException();
                    }
                    
                    if (!isIntegerType(operandInfo.type) && !isPointerType(operandInfo.type)) {
                        Project4SemanticError.unexpectedType(ctx, operandInfo.type).throwException();
                    }
                    
                    return new ExprInfo(operandInfo.type, false); // Result is rvalue
                }
            }

            // Prefix unary operators
            if (ctx.expression().size() == 1) {
                // Prefix increment/decrement
                if ((ctx.INC() != null || ctx.DEC() != null) && ctx.expression().size() == 1) {
                    ExprInfo operandInfo = checkExpression(ctx.expression(0));
                    
                    if (!operandInfo.isLvalue) {
                        Project4SemanticError.lvalueRequired(ctx).throwException();
                    }
                    
                    if (!isIntegerType(operandInfo.type) && !isPointerType(operandInfo.type)) {
                        Project4SemanticError.unexpectedType(ctx, operandInfo.type).throwException();
                    }
                    
                    return new ExprInfo(operandInfo.type, false); // Result is rvalue
                }
                
                // Unary plus/minus
                if (ctx.PLUS() != null || ctx.MINUS() != null) {
                    ExprInfo operandInfo = checkExpression(ctx.expression(0));
                    
                    if (!isIntegerType(operandInfo.type)) {
                        Project4SemanticError.unexpectedType(ctx, operandInfo.type).throwException();
                    }
                    
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
                }
                
                // Logical NOT
                if (ctx.NOT() != null) {
                    ExprInfo operandInfo = checkExpression(ctx.expression(0));
                    
                    if (!isIntegerType(operandInfo.type) && !isPointerType(operandInfo.type)) {
                        Project4SemanticError.unexpectedType(ctx, operandInfo.type).throwException();
                    }
                    
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
                }
                
                // Dereference: STAR expression
                if (ctx.STAR() != null) {
                    ExprInfo operandInfo = checkExpression(ctx.expression(0));
                    
                    if (!(operandInfo.type instanceof PointerType)) {
                        Project4SemanticError.unexpectedType(ctx, operandInfo.type).throwException();
                        return new ExprInfo(new BasicType(BasicType.Kind.INT), true);
                    }
                    
                    Type referencedType = ((PointerType) operandInfo.type).getReferencedType();
                    return new ExprInfo(referencedType, true); // Dereference is lvalue
                }
                
                // Address-of: AMP expression
                if (ctx.AMP() != null) {
                    ExprInfo operandInfo = checkExpression(ctx.expression(0));
                    
                    if (!operandInfo.isLvalue) {
                        Project4SemanticError.lvalueRequired(ctx).throwException();
                    }
                    
                    Type ptrType = new PointerType(operandInfo.type);
                    return new ExprInfo(ptrType, false); // Address-of is rvalue
                }
            }

            // Binary operators
            if (ctx.expression().size() == 2) {
                ExprInfo lhs = checkExpression(ctx.expression(0));
                ExprInfo rhs = checkExpression(ctx.expression(1));
                
                // Arithmetic: *, /, %
                if (ctx.STAR() != null || ctx.DIV() != null || ctx.MOD() != null) {
                    if (!isIntegerType(lhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, lhs.type).throwException();
                    }
                    if (!isIntegerType(rhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, rhs.type).throwException();
                    }
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
                }
                
                // Addition/Subtraction
                if (ctx.PLUS() != null || ctx.MINUS() != null) {
                    Token op = ctx.PLUS() != null ? ctx.PLUS().getSymbol() : ctx.MINUS().getSymbol();
                    
                    // Both integer
                    if (isIntegerType(lhs.type) && isIntegerType(rhs.type)) {
                        return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
                    }
                    
                    // Pointer + integer or integer + pointer (for addition only)
                    if (ctx.PLUS() != null) {
                        if (isPointerType(lhs.type) && isIntegerType(rhs.type)) {
                            return new ExprInfo(lhs.type, false);
                        }
                        if (isIntegerType(lhs.type) && isPointerType(rhs.type)) {
                            return new ExprInfo(rhs.type, false);
                        }
                    }
                    
                    // Pointer - integer
                    if (ctx.MINUS() != null) {
                        if (isPointerType(lhs.type) && isIntegerType(rhs.type)) {
                            return new ExprInfo(lhs.type, false);
                        }
                        // Pointer - pointer
                        if (isPointerType(lhs.type) && isPointerType(rhs.type)) {
                            if (!typesEqual(lhs.type, rhs.type)) {
                                Project4SemanticError.unmatchedTypeForBinaryOP(ctx, op, lhs.type, rhs.type).throwException();
                            }
                            return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
                        }
                    }
                    
                    Project4SemanticError.unmatchedTypeForBinaryOP(ctx, op, lhs.type, rhs.type).throwException();
                }
                
                // Comparison: <, <=, >, >=
                if (ctx.LT() != null || ctx.LE() != null || ctx.GT() != null || ctx.GE() != null) {
                    if (!isIntegerType(lhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, lhs.type).throwException();
                    }
                    if (!isIntegerType(rhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, rhs.type).throwException();
                    }
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
                }
                
                // Equality: ==, !=
                if (ctx.EQ() != null || ctx.NEQ() != null) {
                    Token op = ctx.EQ() != null ? ctx.EQ().getSymbol() : ctx.NEQ().getSymbol();
                    
                    // Check if both are integers or pointers
                    if (!isIntegerType(lhs.type) && !isPointerType(lhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, lhs.type).throwException();
                    }
                    if (!isIntegerType(rhs.type) && !isPointerType(rhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, rhs.type).throwException();
                    }
                    
                    // Special case: allow 0 as null pointer
                    boolean lhsIsZero = isConstantZero(ctx.expression(0));
                    boolean rhsIsZero = isConstantZero(ctx.expression(1));
                    
                    if (!lhsIsZero && !rhsIsZero && !typesEqual(lhs.type, rhs.type)) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx, op, lhs.type, rhs.type).throwException();
                    }
                    
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
                }
                
                // Logical: &&, ||
                if (ctx.AND() != null || ctx.OR() != null) {
                    if (!isIntegerType(lhs.type) && !isPointerType(lhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, lhs.type).throwException();
                    }
                    if (!isIntegerType(rhs.type) && !isPointerType(rhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, rhs.type).throwException();
                    }
                    return new ExprInfo(new BasicType(BasicType.Kind.INT), false);
                }
                
                // Assignment: =
                if (ctx.ASSIGN() != null) {
                    if (!lhs.isLvalue) {
                        Project4SemanticError.lvalueRequired(ctx).throwException();
                    }
                    
                    // Check if lhs or rhs is array type - arrays cannot be assigned
                    if (lhs.type instanceof ArrayType || rhs.type instanceof ArrayType) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx, ctx.ASSIGN().getSymbol(), lhs.type, rhs.type).throwException();
                    }
                    
                    if (!isIntegerType(lhs.type) && !isPointerType(lhs.type)) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx, ctx.ASSIGN().getSymbol(), lhs.type, rhs.type).throwException();
                    }
                    if (!isIntegerType(rhs.type) && !isPointerType(rhs.type)) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx, ctx.ASSIGN().getSymbol(), lhs.type, rhs.type).throwException();
                    }
                    
                    // Special case: allow 0 as null pointer
                    boolean rhsIsZero = isConstantZero(ctx.expression(1));
                    
                    if (!rhsIsZero && !typesEqual(lhs.type, rhs.type)) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx, ctx.ASSIGN().getSymbol(), lhs.type, rhs.type).throwException();
                    }
                    
                    return new ExprInfo(rhs.type, false); // Assignment returns rvalue
                }
            }

            // Should not reach here - all expression cases should be handled above
            throw new RuntimeException("Unexpected expression structure in checkExpression");
        }

        @Override
        public Type visitExpression(SplcParser.ExpressionContext ctx) {
            // This should not be used in Project 4, but keep for compatibility
            return null;
        }

        // Helper methods

        private boolean isConstantZero(SplcParser.ExpressionContext ctx) {
            // Check if expression is directly 0 (optionally wrapped in parentheses)
            if (ctx.Number() != null && ctx.Number().getText().equals("0")) {
                return true;
            }
            // Check if it's parenthesized zero
            if (ctx.LPAREN() != null && ctx.expression().size() == 1 && ctx.Identifier() == null) {
                return isConstantZero(ctx.expression(0));
            }
            return false;
        }

        private boolean isIntegerType(Type type) {
            return type instanceof BasicType && ((BasicType) type).getKind() == BasicType.Kind.INT;
        }

        private boolean isPointerType(Type type) {
            return type instanceof PointerType;
        }

        private boolean typesEqual(Type t1, Type t2) {
            return t1.equals(t2);
        }

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
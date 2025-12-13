package impl;

import framework.AbstractCompiler;
import framework.AbstractGrader;
import framework.lang.Type;
import framework.llvm.*;
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
import org.antlr.v4.runtime.misc.Pair;
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

        // Phase 2: Generate LLVM IR if no semantic errors
        if (!analyzer.hasSemanticErrors()) {
            IRCodeGenerator codeGen = new IRCodeGenerator(grader);
            codeGen.generate(program);
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

        private static final int FILE_SCOPE_ID = 0;
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

        // Add symbol to "tag" namespace (struct tags) - always at file scope
        public boolean addTag(String name, Type type) {
            // Structure tags always have file scope per C standard
            Map<String, Symbol> fileScope = getFileTagScope();
            if (fileScope.containsKey(name)) {
                return false; // Already exists in file scope
            }
            fileScope.put(name, new Symbol(name, type, Symbol.Kind.STRUCT_TAG, FILE_SCOPE_ID));
            return true;
        }

        // Update existing tag at file scope (for completing incomplete structs)
        public void updateTag(String name, Type type) {
            // Structure tags always have file scope per C standard
            Map<String, Symbol> fileScope = getFileTagScope();
            fileScope.put(name, new Symbol(name, type, Symbol.Kind.STRUCT_TAG, FILE_SCOPE_ID));
        }

        // Get file scope for tags (bottom of the stack) - O(1) access
        private Map<String, Symbol> getFileTagScope() {
            return ((ArrayDeque<Map<String, Symbol>>) tagScopes).peekLast();
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
            // Structure tags always have file scope
            Map<String, Symbol> fileScope = getFileTagScope();
            return fileScope.containsKey(name);
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
                        grader.reportSemanticError(Project3SemanticError.definitionIncomplete(getIdentifierNode(ctx.varDec())));
                    } else {
                        // Defer check for direct struct types
                        incompleteGlobals.put(new Symbol(varName, varType, Symbol.Kind.VARIABLE, 0), getIdentifierNode(ctx.varDec()));
                    }
                }

                // Check initialization expression if present using assignment rules [2.2.14]
                if (ctx.expression() != null) {
                    try {
                        ExprInfo exprInfo = checkExpression(ctx.expression());

                        // Check if lhs or rhs is array type - arrays cannot be assigned
                        if (varType instanceof ArrayType || exprInfo.type instanceof ArrayType) {
                            Project4SemanticError.unmatchedTypeForBinaryOP(ctx.expression(), ctx.ASSIGN().getSymbol(), varType, exprInfo.type).throwException();
                        }

                        // Check that both sides are integer or pointer types
                        if (!isIntegerType(varType) && !isPointerType(varType)) {
                            Project4SemanticError.unmatchedTypeForBinaryOP(ctx.expression(), ctx.ASSIGN().getSymbol(), varType, exprInfo.type).throwException();
                        }
                        if (!isIntegerType(exprInfo.type) && !isPointerType(exprInfo.type)) {
                            Project4SemanticError.unmatchedTypeForBinaryOP(ctx.expression(), ctx.ASSIGN().getSymbol(), varType, exprInfo.type).throwException();
                        }

                        // Special case: allow 0 as null pointer
                        boolean rhsIsZero = isConstantZero(ctx.expression());

                        if (!rhsIsZero && !typesEqual(varType, exprInfo.type)) {
                            Project4SemanticError.unmatchedTypeForBinaryOP(ctx.expression(), ctx.ASSIGN().getSymbol(), varType, exprInfo.type).throwException();
                        }
                    } catch (Project4Exception ex) {
                        hasSemanticErrors = true;
                        grader.reportSemanticError(ex);
                    }
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

            // Visit initialization expression if present and check type compatibility
            // Use assignment rules [2.2.14] with unmatchedTypeForBinaryOP for type errors
            if (ctx.expression() != null) {
                try {
                    ExprInfo exprInfo = checkExpression(ctx.expression());

                    // Check if lhs or rhs is array type - arrays cannot be assigned
                    if (varType instanceof ArrayType || exprInfo.type instanceof ArrayType) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx.expression(), ctx.ASSIGN().getSymbol(), varType, exprInfo.type).throwException();
                    }

                    // Check that both sides are integer or pointer types
                    if (!isIntegerType(varType) && !isPointerType(varType)) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx.expression(), ctx.ASSIGN().getSymbol(), varType, exprInfo.type).throwException();
                    }
                    if (!isIntegerType(exprInfo.type) && !isPointerType(exprInfo.type)) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx.expression(), ctx.ASSIGN().getSymbol(), varType, exprInfo.type).throwException();
                    }

                    // Special case: allow 0 as null pointer
                    boolean rhsIsZero = isConstantZero(ctx.expression());

                    if (!rhsIsZero && !typesEqual(varType, exprInfo.type)) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx.expression(), ctx.ASSIGN().getSymbol(), varType, exprInfo.type).throwException();
                    }
                } catch (Project4Exception ex) {
                    hasSemanticErrors = true;
                    grader.reportSemanticError(ex);
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

                // Look up the struct type by tag to get the current (possibly completed) definition
                StructType embeddedStructType = (StructType) structInfo.type;
                Symbol currentTagSymbol = symbolTable.lookupTag(embeddedStructType.getTag());
                StructType structType;
                if (currentTagSymbol != null && currentTagSymbol.getType() instanceof StructType) {
                    structType = (StructType) currentTagSymbol.getType();
                } else {
                    structType = embeddedStructType;
                }

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

                // Look up the struct type by tag to get the current (possibly completed) definition
                StructType embeddedStructType = (StructType) referencedType;
                Symbol currentTagSymbol = symbolTable.lookupTag(embeddedStructType.getTag());
                StructType structType;
                if (currentTagSymbol != null && currentTagSymbol.getType() instanceof StructType) {
                    structType = (StructType) currentTagSymbol.getType();
                } else {
                    structType = embeddedStructType;
                }

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

    // ===== LLVM IR Code Generator =====

    private static class IRCodeGenerator extends SplcBaseVisitor<Void> {
        private final AbstractGrader grader;
        private final IRBuilder ir;
        
        // Symbol info: address + type
        private static class VarInfo {
            final IRValue addr;
            final Type type;
            VarInfo(IRValue addr, Type type) {
                this.addr = addr;
                this.type = type;
            }
        }
        
        // Symbol table for variables (maps variable names to their info)
        private final Map<String, VarInfo> globalVariables = new HashMap<>();
        private final Deque<Map<String, VarInfo>> localScopes = new ArrayDeque<>();
        
        // Current function context
        private FunctionBuilder currentFunction;
        private BasicBlockBuilder currentBlock;
        
        // Struct definitions (maps struct tag to list of (fieldName, fieldType))
        private final Map<String, List<Pair<String, Type>>> structDefs = new LinkedHashMap<>();
        
        // Track struct types for LLVM
        private final Map<String, List<IRType>> structIRTypes = new HashMap<>();

        public IRCodeGenerator(AbstractGrader grader) {
            this.grader = grader;
            this.ir = new IRBuilder();
        }

        public void generate(SplcParser.ProgramContext program) {
            // First pass: collect struct definitions and declare all functions
            for (SplcParser.GlobalDefContext globalDef : program.globalDef()) {
                preprocessGlobalDef(globalDef);
            }
            
            // Second pass: generate code for all definitions
            visit(program);
            
            // Output the generated IR
            grader.printIR(ir);
        }

        // First pass: preprocess to collect struct definitions and declare functions
        private void preprocessGlobalDef(SplcParser.GlobalDefContext ctx) {
            Type specType = buildTypeFromSpecifier(ctx.specifier());
            
            // Handle struct definition in specifier
            if (ctx.specifier().STRUCT() != null && ctx.specifier().LBRACE() != null) {
                String tagName = ctx.specifier().Identifier().getText();
                List<Pair<String, Type>> members = new ArrayList<>();
                List<IRType> irMembers = new ArrayList<>();
                
                for (int i = 0; i < ctx.specifier().specifier().size(); i++) {
                    Type memberSpecType = buildTypeFromSpecifier(ctx.specifier().specifier(i));
                    String memberName = extractIdentifierFromVarDec(ctx.specifier().varDec(i));
                    Type memberType = buildTypeFromVarDec(ctx.specifier().varDec(i), memberSpecType);
                    members.add(new Pair<>(memberName, memberType));
                    irMembers.add(convertTypeToIR(memberType));
                }
                
                structDefs.put(tagName, members);
                structIRTypes.put(tagName, irMembers);
                ir.defineStructure(tagName, irMembers);
            }
            
            // Handle function declaration (without body)
            if (ctx.funcArgs() != null && ctx.SEMI() != null && ctx.LBRACE() == null) {
                String funcName = ctx.Identifier().getText();
                List<Pair<String, IRType>> params = new ArrayList<>();
                
                if (ctx.funcArgs().specifier() != null && !ctx.funcArgs().specifier().isEmpty()) {
                    for (int i = 0; i < ctx.funcArgs().specifier().size(); i++) {
                        Type paramSpecType = buildTypeFromSpecifier(ctx.funcArgs().specifier(i));
                        Type paramType = buildTypeFromVarDec(ctx.funcArgs().varDec(i), paramSpecType);
                        String paramName = extractIdentifierFromVarDec(ctx.funcArgs().varDec(i));
                        params.add(new Pair<>(paramName, convertTypeToIR(paramType)));
                    }
                }
                
                ir.declareFunction(funcName, convertTypeToIR(specType), params);
            }
        }

        @Override
        public Void visitProgram(SplcParser.ProgramContext ctx) {
            for (SplcParser.GlobalDefContext globalDef : ctx.globalDef()) {
                visitGlobalDef(globalDef);
            }
            return null;
        }

        @Override
        public Void visitGlobalDef(SplcParser.GlobalDefContext ctx) {
            Type specType = buildTypeFromSpecifier(ctx.specifier());
            
            if (ctx.LBRACE() != null) {
                // Function definition
                String funcName = ctx.Identifier().getText();
                List<Pair<String, IRType>> params = new ArrayList<>();
                List<Pair<String, Type>> paramTypes = new ArrayList<>();
                
                if (ctx.funcArgs().specifier() != null && !ctx.funcArgs().specifier().isEmpty()) {
                    for (int i = 0; i < ctx.funcArgs().specifier().size(); i++) {
                        Type paramSpecType = buildTypeFromSpecifier(ctx.funcArgs().specifier(i));
                        Type paramType = buildTypeFromVarDec(ctx.funcArgs().varDec(i), paramSpecType);
                        String paramName = extractIdentifierFromVarDec(ctx.funcArgs().varDec(i));
                        params.add(new Pair<>(paramName, convertTypeToIR(paramType)));
                        paramTypes.add(new Pair<>(paramName, paramType));
                    }
                }
                
                currentFunction = ir.defineFunction(funcName, convertTypeToIR(specType), params);
                currentBlock = currentFunction.rootBlock();
                
                // Create local scope for function parameters
                localScopes.push(new HashMap<>());
                
                // Add parameters to local scope with their types
                for (int i = 0; i < paramTypes.size(); i++) {
                    String paramName = paramTypes.get(i).a;
                    Type paramType = paramTypes.get(i).b;
                    IRValue paramAddr = currentFunction.param(paramName);
                    localScopes.peek().put(paramName, new VarInfo(paramAddr, paramType));
                }
                
                // Visit function body
                for (SplcParser.StatementContext stmt : ctx.statement()) {
                    visit(stmt);
                }
                
                // If no return statement was reached, add a default return 0
                if (!currentBlock.hasTerminated()) {
                    currentBlock.ret(IRValue.consti32(0));
                }
                
                localScopes.pop();
                currentFunction = null;
                currentBlock = null;
                
            } else if (ctx.varDec() != null) {
                // Global variable definition
                String varName = extractIdentifierFromVarDec(ctx.varDec());
                Type varType = buildTypeFromVarDec(ctx.varDec(), specType);
                IRType irType = convertTypeToIR(varType);
                
                IRValue globalAddr = ir.defineGlobalVar(varName, irType);
                globalVariables.put(varName, new VarInfo(globalAddr, varType));
            }
            // Skip function declarations (already handled in preprocess) and struct-only declarations
            
            return null;
        }

        @Override
        public Void visitBracket(SplcParser.BracketContext ctx) {
            localScopes.push(new HashMap<>());
            for (SplcParser.StatementContext stmt : ctx.statement()) {
                visit(stmt);
            }
            localScopes.pop();
            return null;
        }

        @Override
        public Void visitVarDecStmt(SplcParser.VarDecStmtContext ctx) {
            Type specType = buildTypeFromSpecifier(ctx.specifier());
            String varName = extractIdentifierFromVarDec(ctx.varDec());
            Type varType = buildTypeFromVarDec(ctx.varDec(), specType);
            IRType irType = convertTypeToIR(varType);
            
            // Allocate local variable
            IRValue varAddr = currentBlock.alloca(irType, varName);
            localScopes.peek().put(varName, new VarInfo(varAddr, varType));
            
            // Handle initialization
            if (ctx.expression() != null) {
                IRValue initValue = generateExpression(ctx.expression());
                currentBlock.store(varAddr, irType, initValue);
            }
            
            return null;
        }

        @Override
        public Void visitIfStmt(SplcParser.IfStmtContext ctx) {
            // Evaluate condition
            IRValue cond = generateExpression(ctx.expression());
            
            // Ensure condition is i1 (bool)
            if (!cond.type().isBoolean()) {
                // Compare with 0 or null to get boolean
                if (cond.type().isPointer()) {
                    cond = currentBlock.icmp(cond, LLVMIcmpPredicate.NotEquals, IRValue.constNull(), null);
                } else {
                    cond = currentBlock.icmp(cond, LLVMIcmpPredicate.NotEquals, IRValue.consti32(0), null);
                }
            }
            
            // Create basic blocks
            BasicBlockBuilder thenBlock = currentFunction.newBasicBlock("if.then");
            BasicBlockBuilder elseBlock = ctx.statement().size() > 1 ? 
                currentFunction.newBasicBlock("if.else") : null;
            BasicBlockBuilder mergeBlock = currentFunction.newBasicBlock("if.end");
            
            // Branch based on condition
            if (elseBlock != null) {
                currentBlock.condBr(cond, thenBlock, elseBlock);
            } else {
                currentBlock.condBr(cond, thenBlock, mergeBlock);
            }
            
            // Generate then block
            currentBlock = thenBlock;
            visit(ctx.statement(0));
            if (!currentBlock.hasTerminated()) {
                currentBlock.br(mergeBlock);
            }
            
            // Generate else block if present
            if (elseBlock != null) {
                currentBlock = elseBlock;
                visit(ctx.statement(1));
                if (!currentBlock.hasTerminated()) {
                    currentBlock.br(mergeBlock);
                }
            }
            
            // Continue with merge block
            currentBlock = mergeBlock;
            
            return null;
        }

        @Override
        public Void visitWhileStmt(SplcParser.WhileStmtContext ctx) {
            // Create basic blocks
            BasicBlockBuilder condBlock = currentFunction.newBasicBlock("while.cond");
            BasicBlockBuilder bodyBlock = currentFunction.newBasicBlock("while.body");
            BasicBlockBuilder endBlock = currentFunction.newBasicBlock("while.end");
            
            // Branch to condition block
            currentBlock.br(condBlock);
            
            // Generate condition block
            currentBlock = condBlock;
            IRValue cond = generateExpression(ctx.expression());
            
            // Ensure condition is i1 (bool)
            if (!cond.type().isBoolean()) {
                if (cond.type().isPointer()) {
                    cond = currentBlock.icmp(cond, LLVMIcmpPredicate.NotEquals, IRValue.constNull(), null);
                } else {
                    cond = currentBlock.icmp(cond, LLVMIcmpPredicate.NotEquals, IRValue.consti32(0), null);
                }
            }
            
            currentBlock.condBr(cond, bodyBlock, endBlock);
            
            // Generate body block
            currentBlock = bodyBlock;
            visit(ctx.statement());
            if (!currentBlock.hasTerminated()) {
                currentBlock.br(condBlock);
            }
            
            // Continue with end block
            currentBlock = endBlock;
            
            return null;
        }

        @Override
        public Void visitReturnStmt(SplcParser.ReturnStmtContext ctx) {
            IRValue retValue = generateExpression(ctx.expression());
            currentBlock.ret(retValue);
            return null;
        }

        @Override
        public Void visitExprStmt(SplcParser.ExprStmtContext ctx) {
            generateExpression(ctx.expression());
            return null;
        }

        // ===== Expression Generation =====
        
        private IRValue generateExpression(SplcParser.ExpressionContext ctx) {
            // Identifier
            if (ctx.Identifier() != null && ctx.LPAREN() == null && ctx.DOT() == null && ctx.ARROW() == null) {
                String name = ctx.Identifier().getText();
                IRValue addr = lookupVariable(name);
                Type varType = lookupVariableType(name);
                IRType irType = convertTypeToIR(varType);
                
                // If it's an array, return the address (arrays decay to pointers)
                if (varType instanceof ArrayType) {
                    return addr;
                }
                
                // Load the value
                return currentBlock.load(addr, irType, null);
            }
            
            // Number
            if (ctx.Number() != null) {
                int value = Integer.parseInt(ctx.Number().getText());
                return IRValue.consti32(value);
            }
            
            // Parenthesized expression
            if (ctx.LPAREN() != null && ctx.expression().size() == 1 && ctx.Identifier() == null) {
                return generateExpression(ctx.expression(0));
            }
            
            // Function call
            if (ctx.Identifier() != null && ctx.LPAREN() != null) {
                String funcName = ctx.Identifier().getText();
                List<IRValue> args = new ArrayList<>();
                
                for (SplcParser.ExpressionContext argCtx : ctx.expression()) {
                    args.add(generateExpression(argCtx));
                }
                
                return currentBlock.call(IRType.int32(), funcName, args, null);
            }
            
            // Array access: expression LBRACK expression RBRACK
            if (ctx.LBRACK() != null) {
                Type baseType = getExpressionType(ctx.expression(0));
                IRValue index = generateExpression(ctx.expression(1));
                
                IRType elementIRType;
                IRValue elementAddr;
                
                if (baseType instanceof ArrayType) {
                    // For arrays, we need the address of the array and use 2-index GEP
                    IRValue baseAddr = generateLvalueAddress(ctx.expression(0));
                    elementIRType = convertTypeToIR(((ArrayType) baseType).getElementType());
                    IRType gepType = convertTypeToIR(baseType);
                    elementAddr = currentBlock.gep(baseAddr, gepType, 0, index, null);
                } else if (baseType instanceof PointerType) {
                    // For pointers, evaluate the pointer expression and use 1-index GEP
                    IRValue ptrValue = generateExpression(ctx.expression(0));
                    Type referenced = ((PointerType) baseType).getReferencedType();
                    elementIRType = convertTypeToIR(referenced);
                    elementAddr = currentBlock.gep(ptrValue, elementIRType, index, null);
                } else {
                    throw new RuntimeException("Array access on non-array, non-pointer type");
                }
                
                return currentBlock.load(elementAddr, elementIRType, null);
            }
            
            // Struct member access: expression DOT Identifier
            if (ctx.DOT() != null) {
                IRValue structAddr = generateLvalueAddress(ctx.expression(0));
                String memberName = ctx.Identifier().getText();
                Type structType = getExpressionType(ctx.expression(0));
                
                if (!(structType instanceof StructType)) {
                    throw new RuntimeException("DOT access on non-struct type");
                }
                
                StructType st = (StructType) structType;
                int memberIndex = getMemberIndex(st.getTag(), memberName);
                Type memberType = getMemberType(st.getTag(), memberName);
                IRType irStructType = IRType.structure(st.getTag());
                IRType memberIRType = convertTypeToIR(memberType);
                
                IRValue memberAddr = currentBlock.gep(structAddr, irStructType, 0, IRValue.consti32(memberIndex), null);
                return currentBlock.load(memberAddr, memberIRType, null);
            }
            
            // Pointer member access: expression ARROW Identifier  
            if (ctx.ARROW() != null) {
                IRValue ptrValue = generateExpression(ctx.expression(0));
                String memberName = ctx.Identifier().getText();
                Type ptrType = getExpressionType(ctx.expression(0));
                
                if (!(ptrType instanceof PointerType)) {
                    throw new RuntimeException("ARROW access on non-pointer type");
                }
                
                Type referencedType = ((PointerType) ptrType).getReferencedType();
                if (!(referencedType instanceof StructType)) {
                    throw new RuntimeException("ARROW access on non-struct pointer");
                }
                
                StructType st = (StructType) referencedType;
                int memberIndex = getMemberIndex(st.getTag(), memberName);
                Type memberType = getMemberType(st.getTag(), memberName);
                IRType irStructType = IRType.structure(st.getTag());
                IRType memberIRType = convertTypeToIR(memberType);
                
                IRValue memberAddr = currentBlock.gep(ptrValue, irStructType, 0, IRValue.consti32(memberIndex), null);
                return currentBlock.load(memberAddr, memberIRType, null);
            }
            
            // Postfix increment/decrement
            if ((ctx.INC() != null || ctx.DEC() != null) && ctx.expression().size() == 1) {
                // Check if it's postfix (expression comes before operator)
                if (isPostfixOp(ctx)) {
                    IRValue addr = generateLvalueAddress(ctx.expression(0));
                    Type varType = getExpressionType(ctx.expression(0));
                    IRType irType = convertTypeToIR(varType);
                    
                    IRValue oldValue = currentBlock.load(addr, irType, null);
                    IRValue one = IRValue.consti32(1);
                    
                    IRValue newValue;
                    if (varType instanceof PointerType) {
                        Type ptrElemType = ((PointerType) varType).getReferencedType();
                        IRType ptrElemIRType = convertTypeToIR(ptrElemType);
                        IRValue offset = ctx.INC() != null ? one : currentBlock.sub(IRValue.consti32(0), one, null);
                        newValue = currentBlock.gep(oldValue, ptrElemIRType, offset, null);
                    } else {
                        newValue = ctx.INC() != null ? 
                            currentBlock.add(oldValue, one, null) :
                            currentBlock.sub(oldValue, one, null);
                    }
                    
                    currentBlock.store(addr, irType, newValue);
                    return oldValue;
                } else {
                    // Prefix increment/decrement
                    IRValue addr = generateLvalueAddress(ctx.expression(0));
                    Type varType = getExpressionType(ctx.expression(0));
                    IRType irType = convertTypeToIR(varType);
                    
                    IRValue oldValue = currentBlock.load(addr, irType, null);
                    IRValue one = IRValue.consti32(1);
                    
                    IRValue newValue;
                    if (varType instanceof PointerType) {
                        Type ptrElemType = ((PointerType) varType).getReferencedType();
                        IRType ptrElemIRType = convertTypeToIR(ptrElemType);
                        IRValue offset = ctx.INC() != null ? one : currentBlock.sub(IRValue.consti32(0), one, null);
                        newValue = currentBlock.gep(oldValue, ptrElemIRType, offset, null);
                    } else {
                        newValue = ctx.INC() != null ? 
                            currentBlock.add(oldValue, one, null) :
                            currentBlock.sub(oldValue, one, null);
                    }
                    
                    currentBlock.store(addr, irType, newValue);
                    return newValue;
                }
            }
            
            // Unary operators (prefix)
            if (ctx.expression().size() == 1) {
                // Unary plus
                if (ctx.PLUS() != null) {
                    return generateExpression(ctx.expression(0));
                }
                
                // Unary minus  
                if (ctx.MINUS() != null) {
                    IRValue operand = generateExpression(ctx.expression(0));
                    return currentBlock.sub(IRValue.consti32(0), operand, null);
                }
                
                // Logical NOT
                if (ctx.NOT() != null) {
                    IRValue operand = generateExpression(ctx.expression(0));
                    IRValue cmp;
                    if (operand.type().isBoolean()) {
                        cmp = currentBlock.icmp(operand, LLVMIcmpPredicate.Equals, IRValue.constFalse(), null);
                    } else if (operand.type().isPointer()) {
                        cmp = currentBlock.icmp(operand, LLVMIcmpPredicate.Equals, IRValue.constNull(), null);
                    } else {
                        cmp = currentBlock.icmp(operand, LLVMIcmpPredicate.Equals, IRValue.consti32(0), null);
                    }
                    return currentBlock.zext(cmp, IRType.int32(), null);
                }
                
                // Dereference
                if (ctx.STAR() != null) {
                    IRValue ptrValue = generateExpression(ctx.expression(0));
                    Type ptrType = getExpressionType(ctx.expression(0));
                    
                    if (!(ptrType instanceof PointerType)) {
                        throw new RuntimeException("Dereference on non-pointer");
                    }
                    
                    Type referencedType = ((PointerType) ptrType).getReferencedType();
                    IRType irType = convertTypeToIR(referencedType);
                    
                    return currentBlock.load(ptrValue, irType, null);
                }
                
                // Address-of
                if (ctx.AMP() != null) {
                    return generateLvalueAddress(ctx.expression(0));
                }
            }
            
            // Binary operators
            if (ctx.expression().size() == 2) {
                // Assignment
                if (ctx.ASSIGN() != null) {
                    IRValue addr = generateLvalueAddress(ctx.expression(0));
                    Type lhsType = getExpressionType(ctx.expression(0));
                    IRType irType = convertTypeToIR(lhsType);
                    IRValue value = generateExpression(ctx.expression(1));
                    
                    // Handle null pointer assignment (0 to pointer)
                    if (irType.isPointer() && value.type().isInteger()) {
                        // Check if rhs is constant 0
                        if (isConstantZero(ctx.expression(1))) {
                            value = IRValue.constNull();
                        }
                    }
                    
                    currentBlock.store(addr, irType, value);
                    return value;
                }
                
                // Logical AND (short-circuit)
                if (ctx.AND() != null) {
                    return generateShortCircuitAnd(ctx);
                }
                
                // Logical OR (short-circuit)
                if (ctx.OR() != null) {
                    return generateShortCircuitOr(ctx);
                }
                
                IRValue lhs = generateExpression(ctx.expression(0));
                IRValue rhs = generateExpression(ctx.expression(1));
                
                // Arithmetic: *, /, %
                if (ctx.STAR() != null) {
                    return currentBlock.mul(lhs, rhs, null);
                }
                if (ctx.DIV() != null) {
                    return currentBlock.div(lhs, rhs, null);
                }
                if (ctx.MOD() != null) {
                    return currentBlock.rem(lhs, rhs, null);
                }
                
                // Addition/Subtraction (including pointer arithmetic)
                if (ctx.PLUS() != null) {
                    Type lhsType = getExpressionType(ctx.expression(0));
                    Type rhsType = getExpressionType(ctx.expression(1));
                    
                    if (lhsType instanceof PointerType && isIntegerType(rhsType)) {
                        // Pointer + integer
                        Type ptrElemType = ((PointerType) lhsType).getReferencedType();
                        IRType ptrElemIRType = convertTypeToIR(ptrElemType);
                        return currentBlock.gep(lhs, ptrElemIRType, rhs, null);
                    } else if (isIntegerType(lhsType) && rhsType instanceof PointerType) {
                        // Integer + pointer
                        Type ptrElemType = ((PointerType) rhsType).getReferencedType();
                        IRType ptrElemIRType = convertTypeToIR(ptrElemType);
                        return currentBlock.gep(rhs, ptrElemIRType, lhs, null);
                    }
                    return currentBlock.add(lhs, rhs, null);
                }
                if (ctx.MINUS() != null) {
                    Type lhsType = getExpressionType(ctx.expression(0));
                    Type rhsType = getExpressionType(ctx.expression(1));
                    
                    if (lhsType instanceof PointerType && isIntegerType(rhsType)) {
                        // Pointer - integer
                        Type ptrElemType = ((PointerType) lhsType).getReferencedType();
                        IRType ptrElemIRType = convertTypeToIR(ptrElemType);
                        IRValue negRhs = currentBlock.sub(IRValue.consti32(0), rhs, null);
                        return currentBlock.gep(lhs, ptrElemIRType, negRhs, null);
                    }
                    return currentBlock.sub(lhs, rhs, null);
                }
                
                // Comparison operators
                LLVMIcmpPredicate pred = null;
                if (ctx.LT() != null) pred = LLVMIcmpPredicate.SignedLT;
                if (ctx.LE() != null) pred = LLVMIcmpPredicate.SignedLE;
                if (ctx.GT() != null) pred = LLVMIcmpPredicate.SignedGT;
                if (ctx.GE() != null) pred = LLVMIcmpPredicate.SignedGE;
                if (ctx.EQ() != null) pred = LLVMIcmpPredicate.Equals;
                if (ctx.NEQ() != null) pred = LLVMIcmpPredicate.NotEquals;
                
                if (pred != null) {
                    // Handle pointer-to-null comparisons
                    Type lhsType = getExpressionType(ctx.expression(0));
                    Type rhsType = getExpressionType(ctx.expression(1));
                    
                    // If one side is a pointer and the other is 0, convert 0 to null
                    if (lhsType instanceof PointerType && isConstantZero(ctx.expression(1))) {
                        rhs = IRValue.constNull();
                    } else if (rhsType instanceof PointerType && isConstantZero(ctx.expression(0))) {
                        lhs = IRValue.constNull();
                    }
                    
                    IRValue cmp = currentBlock.icmp(lhs, pred, rhs, null);
                    return currentBlock.zext(cmp, IRType.int32(), null);
                }
            }
            
            throw new RuntimeException("Unsupported expression: " + ctx.getText());
        }
        
        private IRValue generateShortCircuitAnd(SplcParser.ExpressionContext ctx) {
            // Short-circuit AND: if lhs is false, return 0 without evaluating rhs
            // We use alloca to store the result since we don't have phi
            IRValue resultAddr = currentBlock.alloca(IRType.int32(), "and.result");
            
            // Initialize result to 0 (the short-circuit result)
            currentBlock.store(resultAddr, IRType.int32(), IRValue.consti32(0));
            
            BasicBlockBuilder rhsBlock = currentFunction.newBasicBlock("and.rhs");
            BasicBlockBuilder endBlock = currentFunction.newBasicBlock("and.end");
            
            // Evaluate lhs
            IRValue lhs = generateExpression(ctx.expression(0));
            IRValue lhsBool;
            if (lhs.type().isBoolean()) {
                lhsBool = lhs;
            } else if (lhs.type().isPointer()) {
                lhsBool = currentBlock.icmp(lhs, LLVMIcmpPredicate.NotEquals, IRValue.constNull(), null);
            } else {
                lhsBool = currentBlock.icmp(lhs, LLVMIcmpPredicate.NotEquals, IRValue.consti32(0), null);
            }
            
            // If lhs is true, evaluate rhs; if false, skip to end (result is already 0)
            currentBlock.condBr(lhsBool, rhsBlock, endBlock);
            
            // Evaluate rhs (if we get here, lhs was true)
            currentBlock = rhsBlock;
            IRValue rhs = generateExpression(ctx.expression(1));
            IRValue rhsBool;
            if (rhs.type().isBoolean()) {
                rhsBool = rhs;
            } else if (rhs.type().isPointer()) {
                rhsBool = currentBlock.icmp(rhs, LLVMIcmpPredicate.NotEquals, IRValue.constNull(), null);
            } else {
                rhsBool = currentBlock.icmp(rhs, LLVMIcmpPredicate.NotEquals, IRValue.consti32(0), null);
            }
            IRValue rhsResult = currentBlock.zext(rhsBool, IRType.int32(), null);
            currentBlock.store(resultAddr, IRType.int32(), rhsResult);
            currentBlock.br(endBlock);
            
            currentBlock = endBlock;
            return currentBlock.load(resultAddr, IRType.int32(), null);
        }
        
        private IRValue generateShortCircuitOr(SplcParser.ExpressionContext ctx) {
            // Short-circuit OR: if lhs is true, return 1 without evaluating rhs
            // We use alloca to store the result
            IRValue resultAddr = currentBlock.alloca(IRType.int32(), "or.result");
            
            // Initialize result to 1 (the short-circuit result)
            currentBlock.store(resultAddr, IRType.int32(), IRValue.consti32(1));
            
            BasicBlockBuilder rhsBlock = currentFunction.newBasicBlock("or.rhs");
            BasicBlockBuilder endBlock = currentFunction.newBasicBlock("or.end");
            
            // Evaluate lhs
            IRValue lhs = generateExpression(ctx.expression(0));
            IRValue lhsBool;
            if (lhs.type().isBoolean()) {
                lhsBool = lhs;
            } else if (lhs.type().isPointer()) {
                lhsBool = currentBlock.icmp(lhs, LLVMIcmpPredicate.NotEquals, IRValue.constNull(), null);
            } else {
                lhsBool = currentBlock.icmp(lhs, LLVMIcmpPredicate.NotEquals, IRValue.consti32(0), null);
            }
            
            // If lhs is true, skip to end (result is already 1); if false, evaluate rhs
            currentBlock.condBr(lhsBool, endBlock, rhsBlock);
            
            // Evaluate rhs (if we get here, lhs was false)
            currentBlock = rhsBlock;
            IRValue rhs = generateExpression(ctx.expression(1));
            IRValue rhsBool;
            if (rhs.type().isBoolean()) {
                rhsBool = rhs;
            } else if (rhs.type().isPointer()) {
                rhsBool = currentBlock.icmp(rhs, LLVMIcmpPredicate.NotEquals, IRValue.constNull(), null);
            } else {
                rhsBool = currentBlock.icmp(rhs, LLVMIcmpPredicate.NotEquals, IRValue.consti32(0), null);
            }
            IRValue rhsResult = currentBlock.zext(rhsBool, IRType.int32(), null);
            currentBlock.store(resultAddr, IRType.int32(), rhsResult);
            currentBlock.br(endBlock);
            
            currentBlock = endBlock;
            return currentBlock.load(resultAddr, IRType.int32(), null);
        }
        
        // Generate address of an lvalue expression
        private IRValue generateLvalueAddress(SplcParser.ExpressionContext ctx) {
            // Identifier
            if (ctx.Identifier() != null && ctx.LPAREN() == null && ctx.DOT() == null && ctx.ARROW() == null) {
                return lookupVariable(ctx.Identifier().getText());
            }
            
            // Parenthesized expression
            if (ctx.LPAREN() != null && ctx.expression().size() == 1 && ctx.Identifier() == null) {
                return generateLvalueAddress(ctx.expression(0));
            }
            
            // Array access
            if (ctx.LBRACK() != null) {
                Type baseType = getExpressionType(ctx.expression(0));
                
                if (baseType instanceof ArrayType) {
                    // For arrays, we need the address of the array and use 2-index GEP
                    IRValue baseAddr = generateLvalueAddress(ctx.expression(0));
                    IRValue index = generateExpression(ctx.expression(1));
                    IRType gepType = convertTypeToIR(baseType);
                    return currentBlock.gep(baseAddr, gepType, 0, index, null);
                } else if (baseType instanceof PointerType) {
                    // For pointers, evaluate the pointer expression and use 1-index GEP
                    IRValue ptrValue = generateExpression(ctx.expression(0));
                    IRValue index = generateExpression(ctx.expression(1));
                    Type referenced = ((PointerType) baseType).getReferencedType();
                    IRType elementIRType = convertTypeToIR(referenced);
                    return currentBlock.gep(ptrValue, elementIRType, index, null);
                }
                
                throw new RuntimeException("Array access on non-array, non-pointer");
            }
            
            // Struct member access
            if (ctx.DOT() != null) {
                IRValue structAddr = generateLvalueAddress(ctx.expression(0));
                String memberName = ctx.Identifier().getText();
                Type structType = getExpressionType(ctx.expression(0));
                
                if (!(structType instanceof StructType)) {
                    throw new RuntimeException("DOT access on non-struct");
                }
                
                StructType st = (StructType) structType;
                int memberIndex = getMemberIndex(st.getTag(), memberName);
                IRType irStructType = IRType.structure(st.getTag());
                
                return currentBlock.gep(structAddr, irStructType, 0, IRValue.consti32(memberIndex), null);
            }
            
            // Pointer member access
            if (ctx.ARROW() != null) {
                IRValue ptrValue = generateExpression(ctx.expression(0));
                String memberName = ctx.Identifier().getText();
                Type ptrType = getExpressionType(ctx.expression(0));
                
                if (!(ptrType instanceof PointerType)) {
                    throw new RuntimeException("ARROW access on non-pointer");
                }
                
                Type referencedType = ((PointerType) ptrType).getReferencedType();
                if (!(referencedType instanceof StructType)) {
                    throw new RuntimeException("ARROW access on non-struct pointer");
                }
                
                StructType st = (StructType) referencedType;
                int memberIndex = getMemberIndex(st.getTag(), memberName);
                IRType irStructType = IRType.structure(st.getTag());
                
                return currentBlock.gep(ptrValue, irStructType, 0, IRValue.consti32(memberIndex), null);
            }
            
            // Dereference
            if (ctx.STAR() != null && ctx.expression().size() == 1) {
                return generateExpression(ctx.expression(0));
            }
            
            throw new RuntimeException("Cannot get address of expression: " + ctx.getText());
        }
        
        // ===== Helper Methods =====
        
        private boolean isPostfixOp(SplcParser.ExpressionContext ctx) {
            // In postfix, the operator comes after the expression
            // We can check by looking at token positions
            if (ctx.expression().size() != 1) return false;
            if (ctx.INC() == null && ctx.DEC() == null) return false;
            
            int exprStart = ctx.expression(0).getStart().getStartIndex();
            int opStart = ctx.INC() != null ? 
                ctx.INC().getSymbol().getStartIndex() : 
                ctx.DEC().getSymbol().getStartIndex();
            
            return opStart > exprStart;
        }
        
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
        
        private IRValue lookupVariable(String name) {
            // Check local scopes first (from innermost to outermost)
            for (Map<String, VarInfo> scope : localScopes) {
                if (scope.containsKey(name)) {
                    return scope.get(name).addr;
                }
            }
            
            // Check global variables
            if (globalVariables.containsKey(name)) {
                return globalVariables.get(name).addr;
            }
            
            throw new RuntimeException("Variable not found: " + name);
        }
        
        // Get the type of a variable
        private Type lookupVariableType(String name) {
            // Check local scopes first (from innermost to outermost)
            for (Map<String, VarInfo> scope : localScopes) {
                if (scope.containsKey(name)) {
                    return scope.get(name).type;
                }
            }
            
            // Check global variables
            if (globalVariables.containsKey(name)) {
                return globalVariables.get(name).type;
            }
            
            return new BasicType(BasicType.Kind.INT);  // Fallback
        }
        
        // Get the type of an expression (simplified implementation)
        private Type getExpressionType(SplcParser.ExpressionContext ctx) {
            return getExpressionTypeImpl(ctx);
        }
        
        private Type getExpressionTypeImpl(SplcParser.ExpressionContext ctx) {
            // Identifier
            if (ctx.Identifier() != null && ctx.LPAREN() == null && ctx.DOT() == null && ctx.ARROW() == null) {
                return lookupVariableType(ctx.Identifier().getText());
            }
            
            // Number
            if (ctx.Number() != null) {
                return new BasicType(BasicType.Kind.INT);
            }
            
            // Parenthesized
            if (ctx.LPAREN() != null && ctx.expression().size() == 1 && ctx.Identifier() == null) {
                return getExpressionTypeImpl(ctx.expression(0));
            }
            
            // Function call
            if (ctx.Identifier() != null && ctx.LPAREN() != null) {
                return new BasicType(BasicType.Kind.INT); // All functions return int per project spec
            }
            
            // Array access
            if (ctx.LBRACK() != null) {
                Type baseType = getExpressionTypeImpl(ctx.expression(0));
                if (baseType instanceof ArrayType) {
                    return ((ArrayType) baseType).getElementType();
                } else if (baseType instanceof PointerType) {
                    return ((PointerType) baseType).getReferencedType();
                }
            }
            
            // Struct member access
            if (ctx.DOT() != null) {
                Type structType = getExpressionTypeImpl(ctx.expression(0));
                if (structType instanceof StructType) {
                    String memberName = ctx.Identifier().getText();
                    return getMemberType(((StructType) structType).getTag(), memberName);
                }
            }
            
            // Pointer member access
            if (ctx.ARROW() != null) {
                Type ptrType = getExpressionTypeImpl(ctx.expression(0));
                if (ptrType instanceof PointerType) {
                    Type referencedType = ((PointerType) ptrType).getReferencedType();
                    if (referencedType instanceof StructType) {
                        String memberName = ctx.Identifier().getText();
                        return getMemberType(((StructType) referencedType).getTag(), memberName);
                    }
                }
            }
            
            // Increment/decrement
            if (ctx.INC() != null || ctx.DEC() != null) {
                return getExpressionTypeImpl(ctx.expression(0));
            }
            
            // Unary operators
            if (ctx.expression().size() == 1) {
                if (ctx.PLUS() != null || ctx.MINUS() != null || ctx.NOT() != null) {
                    return new BasicType(BasicType.Kind.INT);
                }
                if (ctx.STAR() != null) {
                    Type ptrType = getExpressionTypeImpl(ctx.expression(0));
                    if (ptrType instanceof PointerType) {
                        return ((PointerType) ptrType).getReferencedType();
                    }
                }
                if (ctx.AMP() != null) {
                    return new PointerType(getExpressionTypeImpl(ctx.expression(0)));
                }
            }
            
            // Binary operators
            if (ctx.expression().size() == 2) {
                if (ctx.ASSIGN() != null) {
                    return getExpressionTypeImpl(ctx.expression(0));
                }
                if (ctx.PLUS() != null || ctx.MINUS() != null) {
                    Type lhsType = getExpressionTypeImpl(ctx.expression(0));
                    Type rhsType = getExpressionTypeImpl(ctx.expression(1));
                    if (lhsType instanceof PointerType) return lhsType;
                    if (rhsType instanceof PointerType) return rhsType;
                    return new BasicType(BasicType.Kind.INT);
                }
                // All other binary ops return int
                return new BasicType(BasicType.Kind.INT);
            }
            
            return new BasicType(BasicType.Kind.INT);
        }
        
        private int getMemberIndex(String structTag, String memberName) {
            List<Pair<String, Type>> members = structDefs.get(structTag);
            if (members == null) return 0;
            for (int i = 0; i < members.size(); i++) {
                if (members.get(i).a.equals(memberName)) {
                    return i;
                }
            }
            return 0;
        }
        
        private Type getMemberType(String structTag, String memberName) {
            List<Pair<String, Type>> members = structDefs.get(structTag);
            if (members == null) return new BasicType(BasicType.Kind.INT);
            for (Pair<String, Type> member : members) {
                if (member.a.equals(memberName)) {
                    return member.b;
                }
            }
            return new BasicType(BasicType.Kind.INT);
        }
        
        private boolean isIntegerType(Type type) {
            return type instanceof BasicType && ((BasicType) type).getKind() == BasicType.Kind.INT;
        }
        
        // ===== Type Conversion =====
        
        private IRType convertTypeToIR(Type type) {
            if (type instanceof BasicType) {
                BasicType.Kind kind = ((BasicType) type).getKind();
                if (kind == BasicType.Kind.INT) {
                    return IRType.int32();
                }
                // CHAR not supported per project spec
                return IRType.int32();
            } else if (type instanceof ArrayType) {
                ArrayType arr = (ArrayType) type;
                IRType elemType = convertTypeToIR(arr.getElementType());
                // Build array type with proper dimensions
                return buildArrayIRType(arr);
            } else if (type instanceof PointerType) {
                return IRType.pointer();
            } else if (type instanceof StructType) {
                return IRType.structure(((StructType) type).getTag());
            }
            return IRType.int32();
        }
        
        private IRType buildArrayIRType(ArrayType arr) {
            // Collect all dimensions
            List<Integer> dims = new ArrayList<>();
            Type current = arr;
            while (current instanceof ArrayType) {
                dims.add(((ArrayType) current).getLength());
                current = ((ArrayType) current).getElementType();
            }
            IRType elemType = convertTypeToIR(current);
            int[] dimArray = new int[dims.size()];
            for (int i = 0; i < dims.size(); i++) {
                dimArray[i] = dims.get(i);
            }
            return IRType.array(elemType, dimArray);
        }
        
        // ===== Type Building from AST =====
        
        private Type buildTypeFromSpecifier(SplcParser.SpecifierContext ctx) {
            if (ctx.INT() != null) {
                return new BasicType(BasicType.Kind.INT);
            } else if (ctx.STRUCT() != null) {
                String tagName = ctx.Identifier().getText();
                if (ctx.LBRACE() != null) {
                    // Complete struct - build the type with members
                    List<StructType.Member> members = new ArrayList<>();
                    for (int i = 0; i < ctx.specifier().size(); i++) {
                        Type memberSpecType = buildTypeFromSpecifier(ctx.specifier(i));
                        String memberName = extractIdentifierFromVarDec(ctx.varDec(i));
                        Type memberType = buildTypeFromVarDec(ctx.varDec(i), memberSpecType);
                        members.add(new StructType.Member(memberType, memberName));
                    }
                    return new StructType(tagName, members);
                } else {
                    // Reference to existing struct
                    List<Pair<String, Type>> existingMembers = structDefs.get(tagName);
                    if (existingMembers != null) {
                        List<StructType.Member> members = new ArrayList<>();
                        for (Pair<String, Type> m : existingMembers) {
                            members.add(new StructType.Member(m.b, m.a));
                        }
                        return new StructType(tagName, members);
                    }
                    return new StructType(tagName, false);
                }
            }
            return new BasicType(BasicType.Kind.INT);
        }
        
        private String extractIdentifierFromVarDec(SplcParser.VarDecContext ctx) {
            if (ctx.Identifier() != null) {
                return ctx.Identifier().getText();
            } else if (ctx.varDec() != null) {
                return extractIdentifierFromVarDec(ctx.varDec());
            }
            return null;
        }
        
        private Type buildTypeFromVarDec(SplcParser.VarDecContext ctx, Type baseType) {
            if (ctx.Identifier() != null) {
                return baseType;
            } else if (ctx.LPAREN() != null && ctx.varDec() != null) {
                return buildTypeFromVarDec(ctx.varDec(), baseType);
            } else if (ctx.LBRACK() != null) {
                int arraySize = Integer.parseInt(ctx.Number().getText());
                Type arrayType = new ArrayType(baseType, arraySize);
                return buildTypeFromVarDec(ctx.varDec(), arrayType);
            } else if (ctx.STAR() != null) {
                Type pointerType = new PointerType(baseType);
                return buildTypeFromVarDec(ctx.varDec(), pointerType);
            }
            return baseType;
        }
    }
}
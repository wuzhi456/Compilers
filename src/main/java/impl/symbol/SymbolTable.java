package impl.symbol;

import framework.lang.Type;
import java.util.*;

public class SymbolTable {
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

package framework.project3;

import org.antlr.v4.runtime.tree.TerminalNode;

public abstract class Project3SemanticError {
    public abstract String printBase();

    public static Project3SemanticError undeclaredUse(TerminalNode identifier) {
        return new UndeclaredUseError(identifier);
    }

    public static Project3SemanticError redefinition(TerminalNode identifier) {
        return new RedefinitionError(identifier);
    }

    public static Project3SemanticError redeclaration(TerminalNode identifier) {
        return new RedeclarationError("", identifier);
    }

    public static Project3SemanticError definitionIncomplete(TerminalNode identifier) {
        return new DefinitionOfIncompleteType(identifier);
    }

    public static Project3SemanticError memberIncomplete(TerminalNode memberIdentifier) {
        return new MemberError(memberIdentifier, " has incomplete type");
    }

    public static Project3SemanticError memberDuplicate(TerminalNode memberIdentifier) {
        return new MemberError(memberIdentifier, " is duplicate");
    }

    private static class UndeclaredUseError extends Project3SemanticError {
        private final TerminalNode identifier;

        public UndeclaredUseError(TerminalNode identifier) {
            this.identifier = identifier;
        }

        @Override
        public String printBase() {
            return String.format("%d:%d: error: Undeclared use of '%s'",
                    identifier.getSymbol().getLine(),
                    identifier.getSymbol().getCharPositionInLine(),
                    identifier.getText()
            );
        }
    }

    private static class RedefinitionError extends Project3SemanticError {
        private final TerminalNode identifier;

        RedefinitionError(TerminalNode identifier) {
            this.identifier = identifier;
        }

        @Override
        public String printBase() {
            return String.format("%d:%d: error: Redefinition of '%s'",
                    identifier.getSymbol().getLine(),
                    identifier.getSymbol().getCharPositionInLine(),
                    identifier.getText()
            );
        }
    }

    private static class RedeclarationError extends Project3SemanticError {
        private final String type;
        private final TerminalNode identifier;

        RedeclarationError(String type, TerminalNode identifier) {
            this.type = type;
            this.identifier = identifier;
        }

        @Override
        public String printBase() {
            return String.format("%d:%d: error: Redeclaration of %s'%s'",
                    identifier.getSymbol().getLine(),
                    identifier.getSymbol().getCharPositionInLine(),
                    type,
                    identifier.getText()
            );
        }
    }

    private static class DefinitionOfIncompleteType extends Project3SemanticError {
        private final TerminalNode identifier;

        DefinitionOfIncompleteType(TerminalNode identifier) {
            this.identifier = identifier;
        }

        @Override
        public String printBase() {
            return String.format("%d:%d: error: Definition of incomplete type '%s'",
                    identifier.getSymbol().getLine(),
                    identifier.getSymbol().getCharPositionInLine(),
                    identifier.getText()
            );
        }
    }

    private static class MemberError extends Project3SemanticError {
        private final TerminalNode memberIdentifier;
        private final String text;

        MemberError(TerminalNode memberIdentifier, String text) {
            this.memberIdentifier = memberIdentifier;
            this.text = text;
        }

        @Override
        public String printBase() {
            return String.format("%d:%d: error: Member '%s'%s",
                    memberIdentifier.getSymbol().getLine(),
                    memberIdentifier.getSymbol().getCharPositionInLine(),
                    memberIdentifier.getText(),
                    text
            );
        }
    }
}

//lexer grammar Splc;
grammar Splc;

// Changes in Project 2: Splc.g4 contains both parser rules and lexer rules.
//  so there should be "grammar Splc;' instead of 'lexer grammer Splc;'

// IDEA Plugin Settings
// - Output Directory: src/main/java/
// - package name: generated.Splc

// =========================
// Parser Rules
// =========================

program: globalDef* EOF;

globalDef
    : specifier Identifier LPAREN funcArgs RPAREN LBRACE statement* RBRACE   // function definition
    | specifier varDec (ASSIGN expression)? SEMI                             // global variable definition
    | specifier SEMI                                                         // global struct declaration
    ;

specifier
    : INT
    | CHAR
    | STRUCT Identifier
    | STRUCT Identifier LBRACE (specifier varDec SEMI)* RBRACE                // complete struct
    ;

varDec
    : Identifier
    | LPAREN varDec RPAREN
    | varDec LBRACK Number RBRACK
    | STAR varDec
    ;

funcArgs
    : (specifier varDec (COMMA specifier varDec)*)?
    ;

statement
    : LBRACE statement* RBRACE                                  #bracket
    | specifier varDec (ASSIGN expression)? SEMI                #VarDecStmt
    | IF LPAREN expression RPAREN statement (ELSE statement)?   #IfStmt
    | WHILE LPAREN expression RPAREN statement                  #WhileStmt
    | RETURN expression SEMI                                    #ReturnStmt
    | expression SEMI                                           #ExprStmt
    ;

expression
    // Primary
    : Identifier
    | Number
    | Char
    | LPAREN expression RPAREN

    // Suffix Unary Operators (postfix) — 高优先级，左结合
    | expression INC
    | expression DEC
    | expression LBRACK expression RBRACK
    | expression DOT Identifier
    | expression ARROW Identifier
    | Identifier LPAREN (expression (COMMA expression)*)? RPAREN

    // Prefix Unary Operators
    | INC expression
    | DEC expression
    | PLUS expression
    | MINUS expression
    | NOT expression
    | STAR expression
    | AMP expression

    // Binary Operators
    | expression (STAR | DIV | MOD) expression
    | expression (PLUS | MINUS) expression
    | expression (LT | LE | GT | GE) expression
    | expression (EQ | NEQ) expression
    | expression AND expression
    | expression OR expression
    | <assoc=right> expression ASSIGN expression
    ;

// =========================
// Lexer Rules
// =========================

// ---------- Keywords ----------
INT     : 'int';
CHAR    : 'char';
STRUCT  : 'struct';
RETURN  : 'return';
IF      : 'if';
ELSE    : 'else';
WHILE   : 'while';

// ---------- Operators ----------
ASSIGN   : '=';
PLUS     : '+';
MINUS    : '-';
STAR     : '*';
DIV      : '/';
MOD      : '%';
LT       : '<';
LE       : '<=';
GT       : '>';
GE       : '>=';
EQ       : '==';
NEQ      : '!=';
AND      : '&&';
OR       : '||';
NOT      : '!';
INC      : '++';
DEC      : '--';
DOT      : '.';
ARROW    : '->';
AMP      : '&';

// ---------- Separators ----------
SEMI     : ';';
COMMA    : ',';
LPAREN   : '(';
RPAREN   : ')';
LBRACE   : '{';
RBRACE   : '}';
LBRACK   : '[';
RBRACK   : ']';

// ---------- Identifiers & Literals ----------
Identifier  : [_a-zA-Z]([a-zA-Z0-9])*;
Number      : '0'|[1-9][0-9]*;
Char        : '\''(~['\\\n\r]|'\\'[nt'\\0])'\'';

// ---------- Whitespace & Comments ----------
WS
    : [ \t\r\n]+ -> skip
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;
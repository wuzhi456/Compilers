lexer grammar Splc;

// IDEA Plugin Settings
// - Output Directory: src/main/java/
// - package name: generated.Splc

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
WHILE   : 'whlie';

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
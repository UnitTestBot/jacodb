lexer grammar HuimpleLexer;

// Keywords
LET: 'let';
CLASS: 'class';
ENUM: 'enum';
INTERFACE: 'interface';
FUNCTION: 'function';
EXPORT: 'export';
NEW: 'new';
FOR: 'for';
IF: 'if';
ELSE: 'else';
WHILE: 'while';
SWITCH: 'switch';
CASE: 'case';
DEFAULT: 'default';
BREAK: 'break';
CONTINUE: 'continue';
RETURN: 'return';
PUBLIC: 'public';
PRIVATE: 'private';
PROTECTED: 'protected';
STATIC: 'static';
CONSTRUCTOR: 'constructor';
EXTENDS: 'extends';
IMPLEMENTS: 'implements';
ABSTRACT: 'abstract';
AS: 'as';

// Operators and Symbols
EQ: '=';
COLON: ':';
SEMI: ';';
COMMA: ',';
DOT: '.';
PLUS: '+';
MINUS: '-';
STAR: '*';
SLASH: '/';
PIPE: '|';
AMP: '&';
OR: '||';
AND: '&&';
CARET: '^';
TILDE: '~';
PERCENT: '%';
QUESTION: '?';
BANG: '!';
LTLT: '<<';
GTGT: '>>';
LT: '<';
GT: '>';
LTE: '<=';
GTE: '>=';
EQEQ: '==';
NEQ: '!=';

LPAREN: '(';
RPAREN: ')';
LBRACE: '{';
RBRACE: '}';
LBRACK: '[';
RBRACK: ']';

// Literals
Literal         : NumericLiteral
                | StringLiteral
                | BooleanLiteral
                | NullLiteral
                | ThisLiteral
                ;

NumericLiteral  : [0-9]+ ('.' [0-9]+)?;
StringLiteral   : '"' (~["\\] | '\\' . )* '"' ;
BooleanLiteral  : 'true' | 'false' ;
NullLiteral     : 'null' ;
ThisLiteral     : 'this' ;

Identifier      : [a-zA-Z_$][a-zA-Z_0-9]* ;

// Comments and Whitespace
LINE_COMMENT: '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT: '/*' .*? '*/' -> skip ;
WS: [ \t\n\r]+ -> skip ;

parser grammar HuimpleParser;

options { tokenVocab=HuimpleLexer; }

program         : (statement | declaration | functionDeclaration | classDeclaration | interfaceDeclaration | enumDeclaration | exportItem)* EOF;

statement       : expressionStatement
                | block
                | ifStatement
                | iterationStatement
                | switchStatement
                | continueStatement
                | breakStatement
                | returnStatement
                | expressionStatement
                ;

declaration     : variableDeclaration;

expressionStatement
                : expression ';' ;

block           : '{' statement* '}' ;

ifStatement     : 'if' '(' expression ')' statement ( 'else' statement )? ;

iterationStatement
                : 'for' '(' (expression? ';' expression? ';' expression?) ')' statement
                | 'while' '(' expression ')' statement
                ;

switchStatement : 'switch' '(' expression ')' '{' caseClause* defaultClause? '}' ;

caseClause      : 'case' expression ':' statement* ;

defaultClause   : 'default' ':' statement* ;

continueStatement
                : 'continue' ';' ;

breakStatement  : 'break' ';' ;

returnStatement : 'return' expression? ';' ;

variableDeclaration
                : 'let' Identifier ':' typeAnnotation ';' ;

typeAnnotation  : Identifier
                | '(' typeAnnotation ')'
                ;

functionDeclaration
                : FUNCTION Identifier '(' parameterList? ')' returnType? block ;

parameterList   : parameter (',' parameter)* ;

parameter       : Identifier ':' typeAnnotation ;

returnType      : ':' typeAnnotation ;

classDeclaration
                : 'class' Identifier (classHeritage)? '{' classBody '}' ;

classHeritage   : 'extends' Identifier (',' Identifier)* ;

classBody       : classElement* ;

classElement    : constructorDeclaration
                | methodDeclaration
                | propertyDeclaration
                ;

constructorDeclaration
                : 'constructor' '(' parameterList? ')' block ;

methodDeclaration
                : 'public'? Identifier '(' parameterList? ')' returnType? block ;

propertyDeclaration
                : 'public'? Identifier ':' typeAnnotation ';' ;

interfaceDeclaration
                : 'interface' Identifier '{' interfaceBody '}' ;

interfaceBody   : interfaceMember* ;

interfaceMember : methodSignature ;

methodSignature : Identifier '(' parameterList? ')' returnType? ';' ;

enumDeclaration : 'enum' Identifier '{' enumMemberList '}' ;

enumMemberList  : enumMember (',' enumMember)* ;

enumMember      : Identifier ;

exportItem      : 'export' '{' Identifier ('as' Identifier)? '}' ';' ;

expression      : Identifier
                | assignmentExpression
                | memberExpression
                | callExpression
                ;

assignmentExpression
                : leftHandSideExpression assignmentOperator expression
                | conditionalExpression
                ;

leftHandSideExpression
                : Identifier
                | memberExpression
                ;

memberExpression
                : primaryExpression ('.' Identifier)*
                ;

callExpression  : memberExpression '(' argumentList? ')' ;

primaryExpression
                : Identifier
                | Literal
                | 'new' memberExpression '(' argumentList? ')'
                | '(' expression ')'
                ;

argumentList    : expression (',' expression)* ;

conditionalExpression
                : logicalORExpression ('?' expression ':' expression)?
                ;

logicalORExpression
                : logicalANDExpression ('||' logicalANDExpression)*
                ;

logicalANDExpression
                : equalityExpression ('&&' equalityExpression)*
                ;

equalityExpression
                : relationalExpression (('==' | '!=') relationalExpression)*
                ;

relationalExpression
                : additiveExpression (('<' | '>' | '<=' | '>=') additiveExpression)*
                ;

additiveExpression
                : multiplicativeExpression (('+' | '-') multiplicativeExpression)*
                ;

multiplicativeExpression
                : unaryExpression (('*' | '/' | '%') unaryExpression)*
                ;

unaryExpression : ('+' | '-' | '!' | '~') unaryExpression
                | primaryExpression
                ;

assignmentOperator
                : '='
                ;

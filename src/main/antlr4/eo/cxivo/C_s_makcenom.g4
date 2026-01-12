// based on the file from... cvičenia z Kompilátorov
// why am I even writing these in English

grammar C_s_makcenom;

initial
    :    statement* EOF
    ;

statement
    :    statementBody EOF?
    |    COMMENT EOF?
    |    NEWLINE
    ;

statementBody
    :   VARIABLE ASSIGNMENT expr SENTENCE_END        # Assignment
    ;

// operators - explicit tokens
// rule labels - for each label a separate visit method is generated
expr
    :    LEFT_PAREN expr RIGHT_PAREN                        # Paren
    |    op=NEGATIVE expr                   # Negative
    |    expr op=(MULTIPLICATION|DIVISION) expr                     # BinaryOperation
    |    expr op=(ADDITION|SUBTRACTION) expr                     # BinaryOperation
    |    NUMBER                                 # Number
    |    VARIABLE                                     # Identifier
    ;

//
// lexer grammar LanguageLexer;

fragment DIGIT
    :    [0-9]
    ;

fragment LETTER
    :    [a-zA-Z]
    ;

// copied from https://github.com/antlr/antlr4/blob/master/doc/lexicon.md
fragment
NameChar
   : NameStartChar
   | '0'..'9'
   | '_'
   | '\u00B7'
   | '\u0300'..'\u036F'
   | '\u203F'..'\u2040'
   ;
fragment
NameStartChar
   : 'A'..'Z' | 'a'..'z'
   | '\u00C0'..'\u00D6'
   | '\u00D8'..'\u00F6'
   | '\u00F8'..'\u02FF'
   | '\u0370'..'\u037D'
   | '\u037F'..'\u1FFF'
   | '\u200C'..'\u200D'
   | '\u2070'..'\u218F'
   | '\u2C00'..'\u2FEF'
   | '\u3001'..'\uD7FF'
   | '\uF900'..'\uFDCF'
   | '\uFDF0'..'\uFFFD'
   ;

ASSIGNMENT
    :    'bude' | 'budú'
    ;

NEGATIVE
    :    'záporný' | 'záporná' | 'záporné' | 'záporní' | 'záporných'
    ;

MULTIPLICATION
    :    'krát'
    ;

DIVISION
    :    'deleno' | 'delené' | 'delená' | 'delení'
    ;


ADDITION
    :    'plus'
    ;

SUBTRACTION
    :    'mínus'
    ;

LEFT_PAREN
    :    '('
    ;

RIGHT_PAREN
    :    ')'
    ;

SENTENCE_END
    : '.'
    ;

BTW
    : 'inak' | 'poznámka' | 'pozn'
    ;

NUMBER
    :    DIGIT+
    ;

VARIABLE
    :    NameStartChar NameChar*
    ;

COMMENT
    : NEWLINE '(' .*? ')'
    ;

NEWLINE
    :    ('\r\n' | '\n' | '\r')
    ;

WS
    :    [ \t]+ -> skip
    ;

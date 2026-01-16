// based on the file from... cvičenia z Kompilátorov
// why am I even writing these in English

grammar C_s_makcenom;

options { caseInsensitive=true; }

initial
    :    statement* EOF
    ;

statement
    :    statementBody SENTENCE_END EOF?
    |    COMMENT EOF?
    |    NEWLINE
    ;

statementBody
    :   LET type=(INT|BOOL|STRING|CHAR) VARIABLE (WHICH_WILL_BE expr)?      # Declaration
    |   IF logic COMMA? THEN statementBody      # Conditional
    |   IF logic COMMA? THEN statementBody COMMA ELSE statementBody      # Conditional
    |   VARIABLE (ASSIGNMENT | LOGIC_ASSIGNMENT) logic        # Assignment
    |   VARIABLE ASSIGNMENT expr        # Assignment
    ;

// operators - explicit tokens
// rule labels - for each label a separate visit method is generated
expr
    :    LEFT_PAREN expr RIGHT_PAREN                        # ExprParen
    |    op=NEGATIVE expr                   # Negative
    |    expr op=(MULTIPLICATION|DIVISION) expr                     # BinaryOperation
    |    expr op=(ADDITION|SUBTRACTION) expr                     # BinaryOperation
    |    NUMBER                                 # Number
    |    VARIABLE                                     # Identifier
    ;

logic
    :   LEFT_PAREN logic RIGHT_PAREN                        # LogicParen
    |   op=NOT logic                           # Negation
    |   logic op=AND logic                     # BinaryLogicOperation
    |   XOR_PREFIX logic op=XOR logic          # BinaryLogicOperation
    |   logic op=OR logic                      # BinaryLogicOperation
    |   expr op=(LESS_THAN | MORE_THAN | LESS_THAN_OR_EQUAL | MORE_THAN_OR_EQUAL | EQUALS | NOT_EQUALS) expr    # BinaryRelationOperation
    |   TRUE                                   # LogicalValue
    |   FALSE                                  # LogicalValue
    |   VARIABLE                               # LogicIdentifier
    ;


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

// Basic language needs

ASSIGNMENT
    :    'bude' | 'budú'
    ;

LOGIC_ASSIGNMENT
    :   'platí keď' | 'platí ak'
    ;

LET
    :   'Majme'
    ;

WHICH_WILL_BE
    :   ', '? 'ktoré bude'
    ;


// Math operations

NEGATIVE
    :    'záporný' | 'záporná' | 'záporné' | 'záporní' | 'záporných'
    ;

MULTIPLICATION
    :    'krát'
    ;

DIVISION
    :    'deleno' | 'delené' | 'delená' | 'delení' | 'delených'
    ;

ADDITION
    :    'plus' | 'a aj'
    ;

SUBTRACTION
    :    'mínus'
    ;



// Logic

TRUE
    : 'áno' | 'pravda'
    ;

FALSE
    : 'nie' | 'nepravda' | 'lož'
    ;

AND
    : ', '? ('a' | 'aj' | 'ani')
    ;

OR
    : ', '? 'či'
    ;

XOR_PREFIX
    : 'buď'
    ;

XOR
    : ', '? 'alebo'
    ;

NOT
    : 'opak'
    ;



// Relation

LESS_THAN
    : 'je menš' ('í' | 'ie'| 'ia') ' ako'
    | 'sú menš' ('í' | 'ie'| 'ia') ' ako'
    ;

MORE_THAN
    : 'je väčš' ('í' | 'ie'| 'ia') ' ako'
    | 'sú väčš' ('í' | 'ie'| 'ia') ' ako'
    ;

LESS_THAN_OR_EQUAL
    : 'je menš' ('í' | 'ie'| 'ia') ' alebo rovn' ('é' | 'í' | 'ý') ' ako'
    | 'sú menš' ('í' | 'ie'| 'ia') ' alebo rovn' ('é' | 'í' | 'ý') ' ako'
    ;

MORE_THAN_OR_EQUAL
    : 'je väčš' ('í' | 'ie'| 'ia') ' alebo rovn' ('é' | 'í' | 'ý') ' ako'
    | 'sú väčš' ('í' | 'ie'| 'ia') ' alebo rovn' ('é' | 'í' | 'ý') ' ako'
    ;

EQUALS
    : 'sa rovná'
    ;

NOT_EQUALS
    : 'sa nerovná'
    ;

// Control flow

IF
    :   'ak' | 'Ak' | 'keby' | 'Keby' | 'keď' | 'Keď'
    ;

THEN
    : ', '? 'tak'
    ;

ELSE
    : ', '? 'inak'
    ;


// Types

INT
    : 'celé číslo'
    ;

BOOL
    : 'pravdivosť'
    ;

STRING
    : 'text'
    ;

CHAR
    : 'znak'
    ;

// Other stuff (very proffesional naming)

LEFT_PAREN
    :    '('
    ;

RIGHT_PAREN
    :    ')'
    ;

INDENT
    : '{'
    ;

DEDENT
    : '}'
    ;

SENTENCE_END
    : '.'
    ;

COMMA
    : ','
    ;

NUMBER
    :    DIGIT+
    ;

VARIABLE
    :    NameStartChar NameChar*
    ;

COMMENT
    : NEWLINE '(' ~('\n' | '\r')* ')'
    ;

NEWLINE
    :    ('\r\n' | '\n' | '\r')
    ;

WS
    :    [ \t]+ -> skip
    ;

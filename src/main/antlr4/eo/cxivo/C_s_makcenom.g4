// based on the file from... cvičenia z Kompilátorov
// why am I even writing these in English

grammar C_s_makcenom;

options { caseInsensitive=true; }

initial
    :    statement* EOF
    ;

statement
    :    statementBody SENTENCE_END? EOF?
    |    COMMENT EOF?
    |    NEWLINE
    ;

statementBody
    :   LET type VARIABLE (WHICH_WILL_BE (num_expr | logic_expr | array_expr))?      # Declaration
    |   IF logic_expr COMMA? THEN (statementBody | block) ELSE (statementBody | block)         # Conditional
    |   IF logic_expr COMMA? THEN (statementBody | block)                                  # Conditional
    |   LOAD input_type INTO_VAR VARIABLE                                   # Input
    |   PRINT (num_expr | logic_expr) AND_PRINT_NEWLINE?                             # Output
    |   PRINT_NEWLINE                                                       # PrintNewLine
    |   id (ASSIGNMENT | LOGIC_ASSIGNMENT) logic_expr                      # Assignment
    |   id ASSIGNMENT (num_expr | array_expr)                                         # Assignment
    ;

block
    :   INDENT statement* DEDENT
    ;

id
    :   (VARIABLE | FIRST | LAST) ('-tý ' | '-ty ')? 'prvok zoznamu' VARIABLE                      # ArrayElement
    |   'prvok zoznamu' VARIABLE 'na pozícii' LEFT_PAREN (num_expr COMMA)* num_expr RIGHT_PAREN    # ArrayElement
    |   (VARIABLE | FIRST | LAST) ('-tý ' | '-ty ')? 'znak textu' VARIABLE                         # CharOfText
    |   (VARIABLE | FIRST | LAST) ('-te ')? 'písmeno textu' VARIABLE                               # CharOfText
    |   'znak textu' VARIABLE 'na pozícii' LEFT_PAREN (num_expr COMMA)* num_expr RIGHT_PAREN       # CharOfText
    |   VARIABLE                                                                                   # Variable
    ;

// operators - explicit tokens
// rule labels - for each label a separate visit method is generated
num_expr
    :    LEFT_PAREN num_expr RIGHT_PAREN                                # ExprParen
    |    op=NEGATIVE num_expr                                           # Negative
    |    num_expr op=(MULTIPLICATION|DIVISION) num_expr                 # BinaryOperation
    |    num_expr op=(ADDITION|SUBTRACTION) num_expr                    # BinaryOperation
    |    NUMBER                                                         # Number
    |    ('dĺžka zoznamu' | 'dĺžka textu') VARIABLE                     # ArraySize
    |    id                                                             # Identifier
    ;

logic_expr
    :   LEFT_PAREN logic_expr RIGHT_PAREN                   # LogicParen
    |   op=NOT logic_expr                                   # Negation
    |   logic_expr op=AND logic_expr                        # BinaryLogicOperation
    |   XOR_PREFIX logic_expr op=XOR logic_expr             # BinaryLogicOperation
    |   logic_expr op=OR logic_expr                         # BinaryLogicOperation
    |   num_expr op=(LESS_THAN | MORE_THAN | LESS_THAN_OR_EQUAL | MORE_THAN_OR_EQUAL | EQUALS | NOT_EQUALS) num_expr    # BinaryRelationOperation
    |   TRUE                                                # LogicalValue
    |   FALSE                                               # LogicalValue
    |   id                                                  # LogicIdentifier
    ;

array_expr
    :   LEFT_SQUARE (((num_expr COMMA)* num_expr?) | ((logic_expr COMMA)* logic_expr?) | ((array_expr COMMA)* array_expr)) RIGHT_SQUARE
    ;

type
    : INT | BOOL | STRING | CHAR | LIST of_type
    ;

input_type
    : type | LINE | WORD
    ;

of_type
    : OF_INTS | OF_BOOLS | OF_STRINGS | OF_CHARS | OF_LISTS of_type
    ;


// lexer grammar LanguageLexer;

fragment DIGIT
    :    [0-9]
    ;

fragment LETTER
    :    [a-z]
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
   : 'a'..'z'
   | '\u00C0'..'\u00D6'
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
    :   ', '? ('ktoré bude' | 'ktorá bude' | 'ktorý bude' | 'ktoré budú' | 'ktorí budú')
    ;


// Input/output
LOAD
    :   'Načítaj'
    ;

INTO_VAR
    :   'do premennej'
    ;

PRINT
    :   'Vypíš'
    ;

AND_PRINT_NEWLINE
    :   'a odriadkuj'
    ;

PRINT_NEWLINE
    :   'Odriadkuj'
    ;

FIRST
    :   'prvý' | 'prvé'
    ;

LAST
    :   'posledný' | 'posledné'
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
INT: 'celé číslo';
OF_INTS: 'celých čísel';

BOOL: 'pravdivosť';
OF_BOOLS: 'pravdivostí';

STRING: 'text';
OF_STRINGS: 'textov';

CHAR: 'znak';
OF_CHARS: 'znakov';

LINE
    : 'riadok'
    ;

WORD
    : 'slovo'
    ;

LIST: 'zoznam';
OF_LISTS: 'zoznamov';

// Other stuff (very proffesional naming)

LEFT_PAREN
    :    '('
    ;

RIGHT_PAREN
    :    ')'
    ;

LEFT_SQUARE
    :    '['
    ;

RIGHT_SQUARE
    :    ']'
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

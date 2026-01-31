// based on the file from... cvičenia z Kompilátorov
// why am I even writing these in English

grammar C_s_makcenom;

options { caseInsensitive=true; }

initial
    :    statement* EOF
    ;

statement
    :    functionDefinition EOF?                # StatementFunction
    |    statementBody SENTENCE_END? EOF?       # StatementWithBody
    |    COMMENT EOF?                           # Comment
    |    NEWLINE                                # Newline
    ;

statementBody
    :   LET var_type=type VARIABLE (WHICH_WILL_BE expr)?                                                    # Declaration
    |   IF condition=logic_expr COMMA? THEN (statementBody | block) (ELSE (statementBody | block))?         # Conditional
    |   'Opakuj pre' varName=VARIABLE 'od' lower=num_expr 'po' upper=num_expr ':' (statementBody | block)   # ForLoop
    |   'Opakuj od' lower=num_expr 'po' upper=num_expr ':' (statementBody | block)                          # ForLoop
    |   'Kým' condition=logic_expr THEN (statementBody | block)                                             # WhileLoop
    |   LOAD input_type INTO_VAR VARIABLE                                   # Input
    |   PRINT (expr) AND_PRINT_NEWLINE?                                     # Output
    |   PRINT_NEWLINE                                                       # PrintNewLine
    |   BREAK                           # Break
    |   CONTINUE                        # Continue
    |   DONE                            # ReturnNothing
    |   RETURN expr                     # Return
    |   'Pridaj do zoznamu' array=id 'prvok' expr                     # AddElement
    |   ('Zmaž' | 'Odstráň') ' zo zoznamu' array=id                   # RemoveElement
    |   function_expr                                           # ProcedureCall
    |   id op=(LOGIC_ASSIGNMENT | ASSIGNMENT) expr              # Assignment
    ;

functionDefinition
    : 'Funkcia' name=VARIABLE ('vracajúca' returning=type | 'nevracajúca nič') 'berie' (in_type=type in_name=VARIABLE (COMMA | AND))* in_type=type in_name=VARIABLE 'a robí:' INDENT statement* DEDENT
    ;

block
    :   INDENT statement* DEDENT
    ;

id
    :   (index=VARIABLE ('-tý ' | '-ty ')? | FIRST | LAST) 'prvok zoznamu' array=VARIABLE                      # ArrayElement
    |   'prvok zoznamu' array=VARIABLE 'na pozícii' LEFT_PAREN (num_expr COMMA)* num_expr RIGHT_PAREN          # ArrayElement
    |   (index=VARIABLE ('-tý ' | '-ty ')? | FIRST | LAST) 'znak textu' array=VARIABLE                         # CharOfText
    |   (index=VARIABLE ('-te ')? | FIRST | LAST) 'písmeno textu' array=VARIABLE                               # CharOfText
    |   'znak textu' array=VARIABLE 'na pozícii' LEFT_PAREN (num_expr COMMA)* num_expr RIGHT_PAREN             # CharOfText
    |   VARIABLE                                                                                               # Variable
    ;

expr: num_expr | logic_expr | array_expr | TEXT | CHARACTER | function_expr;

function_expr
    :   name=VARIABLE LEFT_PAREN (expr COMMA)* expr RIGHT_PAREN
    ;

num_expr
    :    LEFT_PAREN num_expr RIGHT_PAREN                                # ExprParen
    |    op=NEGATIVE num_expr                                           # Negative
    |    left=num_expr op=(MULTIPLICATION|DIVISION|MODULO) right=num_expr      # BinaryOperation
    |    left=num_expr op=(ADDITION|SUBTRACTION) right=num_expr         # BinaryOperation
    |    NUMBER                                                         # Number
    |    ('dĺžka' | 'dĺžku') (' zoznamu' | ' textu') id                     # ArraySize
    |    id                                                             # Identifier
    ;

logic_expr
    :   LEFT_PAREN logic_expr RIGHT_PAREN                              # LogicParen
    |   op=NOT logic_expr                                              # Negation
    |   left=logic_expr op=AND right=logic_expr                        # BinaryLogicOperation
    |   XOR_PREFIX left=logic_expr op=XOR right=logic_expr             # BinaryLogicOperation
    |   left=logic_expr op=OR right=logic_expr                         # BinaryLogicOperation
    |   left=num_expr op=(LESS_THAN | MORE_THAN | LESS_THAN_OR_EQUAL | MORE_THAN_OR_EQUAL | EQUALS | NOT_EQUALS) right=num_expr    # BinaryRelationOperation
    |   val=TRUE                                                            # LogicalValue
    |   val=FALSE                                                           # LogicalValue
    |   id                                                              # LogicIdentifier
    ;

array_expr
    :   LEFT_SQUARE ((expr COMMA)* expr?) RIGHT_SQUARE
    ;

type
    : INT | BOOL | STRING | CHAR | LIST OF_LISTS* (of_primitives | OF_STRINGS) | TABLE of_primitives OF_SIZE NUMBER (MULTIPLICATION NUMBER)*
    ;

input_type
    : INT | BOOL | LINE | WORD | CHAR
    ;

of_primitives
    : OF_INTS | OF_BOOLS | OF_CHARS
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
NameStartChar:
   'a' | 'á' | 'ä' | 'b' | 'c' | 'č' | 'd' | 'ď' | 'e' | 'é' | 'f' | 'g' | 'h' | 'i' | 'í' | 'j' | 'k' | 'l' | 'ĺ' | 'ľ' | 'm' | 'n' | 'ň' | 'o' | 'ó' | 'ô' | 'p' | 'q' | 'r' | 'ŕ' | 'ř' | 's' | 'š' | 't' | 'ť' | 'u' | 'ú' | 'ů' | 'v' | 'w' | 'x' | 'y' | 'ý' | 'z' | 'ž';

 /*
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
*/

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

MODULO
    :   'modulo' | 'zvyšok po delení číslom'
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
    : 'sa rovná' | 'je rovné' | 'je rovný' | 'je rovná' | 'sú rovné' | 'sú rovní'
    ;

NOT_EQUALS
    : 'sa nerovná' | 'je nerovné' | 'je nerovný' | 'je nerovná' | 'sú nerovné' | 'sú nerovní'
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

TABLE: 'tabuľka' | 'tabuľku';

OF_SIZE: 'o veľkosti' | 'o rozmere' | 'o rozmeroch';

// Program flow
BREAK: 'Dlabať';
CONTINUE: 'Preskoč';
DONE: 'Hotovo';
RETURN: 'Vráť';


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
    :    '-'? DIGIT+
    ;

VARIABLE
    :    NameStartChar NameChar*
    ;

CHARACTER
    : '\'' ( . | '\\' . ) '\''
    ;

TEXT
    : '"' (~('\n' | '\r' | '"') | '\\"')* '"'
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

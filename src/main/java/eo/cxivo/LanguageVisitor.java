package eo.cxivo;

import org.stringtemplate.v4.*;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;


public class LanguageVisitor extends C_s_makcenomBaseVisitor<CodeFragment> {
    STGroup templates = new STGroupFile("helper-libs/basics.stg");

    // new HashSet get pushed with every new scope
    private Stack<HashMap<String, VariableInfo>> variables = new Stack<>();
    private final ErrorCollector errorCollector;
    private final List<CodeFragment> declarations = new ArrayList<>();
    private final HashMap<String, FunctionInfo> functions = new HashMap<>();
    private FunctionInfo currentFunction = null;
    private final Stack<List<VariableInfo>> garbageCollectAfterFunction = new Stack<>();
    private final Stack<String> breakLabels = new Stack<>();
    private final Stack<String> continueLabels = new Stack<>();
    private boolean justExitedFunction = false;

    private int labelIndex = 0;

    // from https://www.baeldung.com/java-remove-accents-from-text
    static String toLowerCaseASCII(String input) {
        return Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    }

    private String generateUniqueRegisterName(String originalName) {
        if (originalName.isEmpty()) {
            return "%reg_" + labelIndex++;
        } else {
            return "%" + toLowerCaseASCII(originalName) + "_" + labelIndex++;
        }
    }

    private String generateNewLabel() {
        return Integer.toString(labelIndex++);
    }


    private boolean isVariableNameUsed(String name) {
        return variables.stream().anyMatch(map -> map.containsKey(name.toLowerCase()));
    }

    private VariableInfo getVariableInfo(String name, int line) {
        // search in defined variables
        for (HashMap<String, VariableInfo> map: variables) {
            if (map.containsKey(name.toLowerCase())) {
                return map.get(name.toLowerCase());
            }
        }

        errorCollector.add("Problém na riadku " + line
                + ": Neznámy identifikátor \"" + name + "\" (inak, v Č treba pred použitím deklarovať)");

        return new VariableInfo("", Type.VOID);
    }

    public LanguageVisitor(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }


    protected static class ArrayAssignmentTemplate {
        protected CodeFragment code;    // for calculating the value
        protected List<Integer> position = new ArrayList<>();   // position of the element in the array

        protected ArrayAssignmentTemplate(CodeFragment code, int pos) {
            this.code = code;
            position.add(pos);
        }
    }


    protected static class TableRepresentation {
        protected String arrayConstant;     // a LLVM constant
        protected List<ArrayAssignmentTemplate> calculations = new ArrayList<>();  // info for later assignment
    }


    private TableRepresentation createTableConstant(C_s_makcenomParser.Array_exprContext context, Type mustBeType) {
        List<String> constantPartOfArray = new ArrayList<>();
        TableRepresentation tableRepresentation = new TableRepresentation();

        // type with one less table dimension
        Type innerType = new Type(mustBeType.primitive);
        innerType.tableLengths = new ArrayList<>(mustBeType.tableLengths);
        innerType.tableLengths.removeFirst();

        if (mustBeType.tableLengths.size() == 1) {
            // go through the array and visit all elements
            for (int i = 0; context.expr(i) != null; i++) {
                // we should be only expecting other types of expressions according to our type
                if (context.expr(i).array_expr() != null) {
                    errorCollector.add("Problém na riadku " + context.getStart().getLine()
                            + ": Tabuľka na pravej strane má viac rozmerov než by mala mať");
                    return new TableRepresentation();
                }

                CodeFragment element = visit(context.expr(i));

                // test if they are the correct type
                if (!element.type.equals(innerType)) {
                    errorCollector.add("Problém na riadku " + context.getStart().getLine()
                            + ": Nekompatibilné typy (teraz to bude technické): premenná \"" + innerType.getNameInLLVM()
                            + " a hodnota \"" + element.type.getNameInLLVM() + "\"");
                    return new TableRepresentation();
                }

                // this tests for constants, which can just be plopped into the LLVM array constant
                if (context.expr(i).CHARACTER() != null
                        || (context.expr(i).num_expr() != null && context.expr(i).num_expr() instanceof C_s_makcenomParser.NumberContext)
                        || (context.expr(i).logic_expr() != null && context.expr(i).logic_expr() instanceof C_s_makcenomParser.LogicalValueContext)) {


                    // this holds the value, and there is no code
                    constantPartOfArray.add(innerType.getNameInLLVM() + " " + element.resultRegisterName);
                } else {
                    // we need a different approach
                    // we add a zero or whatever to the constant array
                    constantPartOfArray.add(innerType.getNameInLLVM() + " 0");

                    // and we add the code to set the position to the correct value
                    tableRepresentation.calculations.add(new ArrayAssignmentTemplate(element, i));

                    // the setting itself will be done later
                }
            }
        } else {
            // recursion into the table!


            // for all parts of the array
            for (int i = 0; context.expr(i) != null; i++) {
                if (context.expr(i).array_expr() == null) {
                    // it is possible that the user is assigning a different table
                    CodeFragment code = visit(context.expr(i));

                    // NOW check the type
                    if (!code.type.equals(mustBeType)) {
                        // nope, it's not correct
                        errorCollector.add("Problém na riadku " + context.getStart().getLine()
                                + ": Tabuľka na pravej strane má menej rozmerov než by mala mať");
                        return new TableRepresentation();
                    }

                    // if everything checks out, create a dummy array of the correct size
                    String dummy = mustBeType.getBaseTypeNameInLLVM() + " 0";
                    for (int j = mustBeType.tableLengths.size() - 1; j > 0; j--) {
                        // create a dummy type
                        Type layerType = Type.copyOf(mustBeType);
                        layerType.tableLengths = layerType.tableLengths.subList(j, mustBeType.tableLengths.size());

                        dummy = layerType.getNameInLLVM() + "["
                                + String.join(", ", Collections.nCopies(layerType.tableLengths.getFirst(), dummy)) + "]";
                    }

                    constantPartOfArray.add(dummy);
                    tableRepresentation.calculations.add(new ArrayAssignmentTemplate(code, i));
                } else {
                    // recursively visit square bracketed
                    TableRepresentation part = createTableConstant(context.expr(i).array_expr(), innerType);

                    // add the position in the array
                    final int finalI = i;
                    part.calculations.forEach(c -> c.position.addFirst(finalI));

                    // collect everything generated
                    constantPartOfArray.add(part.arrayConstant);
                    tableRepresentation.calculations.addAll(part.calculations);
                }
            }
        }

        // create the LLVM array constant
        tableRepresentation.arrayConstant = mustBeType.getNameInLLVM() + " [" + String.join(", ", constantPartOfArray) + "]";
        return tableRepresentation;
    }

    private CodeFragment loadVariableIntoRegister(VariableInfo variable) {
        ST getFromVariableTemplate = templates.getInstanceOf("GetFromVariable");

        getFromVariableTemplate.add("memory_register", variable.nameInCode);
        getFromVariableTemplate.add("type", variable.type.getNameInLLVM());
        String uniqueName = generateUniqueRegisterName("");
        getFromVariableTemplate.add("return_register", uniqueName);

        return new CodeFragment(getFromVariableTemplate.render(), uniqueName, variable.type);
    }

    // returns the pointer to the string
    private VariableInfo createTextConstant(String text) {
        text = text.replaceAll("\\\\\\\\", "\\\\5C")
                .replaceAll("\\\\t", "\\\\09")
                .replaceAll("\\\\0", "\\\\00")
                .replaceAll("\\\\n", "\\\\0D\\\\0A");


        // every "\XX" code is just 1 byte, sooo we treat it as such
        int backslashCount = text.length() - text.replace("\\", "").length();
        int length = text.getBytes().length - 2 * backslashCount;

        // create a global constant
        ST constantTextTemplate = templates.getInstanceOf("TextConstantDeclaration");
        constantTextTemplate.add("size", length + 1);
        constantTextTemplate.add("text", text);
        String returnRegister = "@text_" + generateNewLabel();
        constantTextTemplate.add("return_register", returnRegister);
        declarations.add(new CodeFragment(constantTextTemplate.render()));

        Type type = new Type(Type.Primitive.CHAR);
        type.tableLengths.add(length);

        return new VariableInfo(returnRegister, type);
    }


    private int nearestLargerPowerOf2(int x) {
        x--;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return x + 1;
    }

    // generates the code for assignment, to be used by multiple visitors
    // need only either context or calculated value
    // stores result in the provided variableInfo
    // returns CodeFragment with the whole operation
    private CodeFragment assignment(VariableInfo variableInfo, C_s_makcenomParser.ExprContext context, int line, CodeFragment calculatedValue, boolean mustBeBool, boolean shouldGarbageCollect) {
        // just for beauty, enforce correct usage of booleans
        if (mustBeBool && variableInfo.type.primitive != Type.Primitive.BOOL) {
            errorCollector.add("Problém na riadku " + line
                    + ": Pri \"platí ak\" má byť pravdivostná hodnota, nie nejaké " + context.getText());
            return new CodeFragment();
        }

        ST variableAssignmentTemplate = templates.getInstanceOf("VariableAssignment");

        if (calculatedValue != null) {
            // we use the CodeFragment
            if (!variableInfo.type.equals(calculatedValue.type)) {
                errorCollector.add("Problém na riadku " + context.getStart().getLine()
                        + ": Nekompatibilné typy (teraz to bude technické): premenná \"" + variableInfo.type.getNameInLLVM()
                        + " a hodnota \"" + calculatedValue.type.getNameInLLVM() + "\"");
                return new CodeFragment();
            }

            if (calculatedValue.type.listDimensions > 0) {
                // increase refcount
                ST listTemplate = templates.getInstanceOf("ListAssign");

                listTemplate.add("label_id", generateNewLabel());
                listTemplate.add("layers", variableInfo.type.listDimensions - 1);
                listTemplate.add("new_register", calculatedValue.resultRegisterName);
                listTemplate.add("calculate_new", calculatedValue);
                listTemplate.add("memory_register", variableInfo.nameInCode);

                return new CodeFragment(listTemplate.render(), variableInfo.nameInCode, variableInfo.type);
            } else {
                // regular var
                variableAssignmentTemplate.add("memory_register", variableInfo.nameInCode);
                variableAssignmentTemplate.add("type", variableInfo.type.getNameInLLVM());
                variableAssignmentTemplate.add("compute_value", calculatedValue);
                variableAssignmentTemplate.add("value_register", calculatedValue.resultRegisterName);
                return new CodeFragment(variableAssignmentTemplate.render(), variableInfo.nameInCode, variableInfo.type);
            }
        } else if (!variableInfo.type.tableLengths.isEmpty() && context.array_expr() != null) {
            // TABLE

            TableRepresentation tableRepresentation = createTableConstant(context.array_expr(), variableInfo.type);
            variableAssignmentTemplate.add("value_register", tableRepresentation.arrayConstant);
            variableAssignmentTemplate.add("memory_register", variableInfo.nameInCode);
            // we do NOT set the type, it's already built in the constant


            // puts correct non-compile time constant values into the correct places
            StringBuilder particularElements = new StringBuilder();

            tableRepresentation.calculations.forEach(x -> {
                ST setTableElementTemplate = templates.getInstanceOf("SetTableElement");

                // common for all elements
                setTableElementTemplate.add("memory_register", variableInfo.nameInCode);
                setTableElementTemplate.add("type", variableInfo.type.getNameInLLVM());
                setTableElementTemplate.add("base_type", variableInfo.type.getBaseTypeNameInLLVM());

                setTableElementTemplate.add("label_id", generateNewLabel());
                setTableElementTemplate.add("calculate_value", x.code);
                setTableElementTemplate.add("value_register", x.code.resultRegisterName);

                // add correct position for
                setTableElementTemplate.add("index_registers",
                        x.position.stream().map(j -> "i32 " + j).collect(Collectors.joining(", ")));

                particularElements.append(setTableElementTemplate.render()).append("\r\n");
            });

            variableAssignmentTemplate.add("code_after", particularElements.toString());
            return new CodeFragment(variableAssignmentTemplate.render(), variableInfo.nameInCode, variableInfo.type);

        } else if (variableInfo.type.listDimensions > 0) {
            // LIST
            CodeFragment listCalculation;

            if (context.array_expr() != null) {
                // a new list from square brackets
                // type with one less list dimension
                Type innerType = Type.copyOf(variableInfo.type);
                innerType.listDimensions--;

                int arraySize = context.array_expr().expr().size();

                // create a new list
                ST listTemplate = templates.getInstanceOf("ListCreation");
                listTemplate.add("label_id", generateNewLabel());
                String arrayPointer = generateUniqueRegisterName("array");
                listTemplate.add("array_register", arrayPointer);
                String listPointer = generateUniqueRegisterName("list");
                listTemplate.add("return_register", listPointer);
                listTemplate.add("has_value", arraySize > 0 ? 1 : 0);
                listTemplate.add("size", arraySize);
                listTemplate.add("capacity", nearestLargerPowerOf2(arraySize));
                listTemplate.add("type", innerType.getNameInLLVM());


                // for each element
                for (int i = 0; i < arraySize; i++) {
                    VariableInfo elementInfo = new VariableInfo(generateUniqueRegisterName(""), innerType);

                    ST locateTemplate = templates.getInstanceOf("LocateArrayElement");
                    locateTemplate.add("type", innerType.getNameInLLVM());
                    locateTemplate.add("memory_register", arrayPointer);
                    locateTemplate.add("return_register", elementInfo.nameInCode);
                    locateTemplate.add("index_registers", "i32 " + i);

                    // recursion, this will make an assignment to the correct place in our array
                    CodeFragment element = assignment(elementInfo, context.array_expr().expr(i), line, null, false, false);

                    listTemplate.add("code_after", "\r\n" + locateTemplate.render() + "\r\n" + element);
                }

                listCalculation = new CodeFragment(listTemplate.render(), listPointer, variableInfo.type);
            } else if (context.TEXT() != null) {
                // TEXT
                if (variableInfo.type.primitive != Type.Primitive.CHAR || variableInfo.type.listDimensions != 1) {
                    errorCollector.add("Problém na riadku " + context.getStart().getLine()
                            + ": Nekompatibilné typy: text nemožno priradiť do " + variableInfo.type.getNameInLLVM());
                    return new CodeFragment();
                }

                String text = context.TEXT().getText().substring(1, context.TEXT().getText().length() - 1);

                VariableInfo globalText = createTextConstant(text);

                int arraySize = globalText.type.tableLengths.getFirst() + 1;

                // create a new list
                ST listTemplate = templates.getInstanceOf("ListCreation");
                listTemplate.add("label_id", generateNewLabel());
                String arrayPointer = generateUniqueRegisterName("array");
                listTemplate.add("array_register", arrayPointer);
                String listPointer = generateUniqueRegisterName("list");
                listTemplate.add("return_register", listPointer);
                listTemplate.add("has_value", arraySize > 0 ? 1 : 0);
                listTemplate.add("size", arraySize);
                listTemplate.add("capacity", nearestLargerPowerOf2(arraySize));
                listTemplate.add("type", "i8");

                // add characters
                ST arrayTemplate = templates.getInstanceOf("PutConstantList");
                arrayTemplate.add("array_register", arrayPointer);
                arrayTemplate.add("label_id", generateNewLabel());
                arrayTemplate.add("size", arraySize);

                arrayTemplate.add("value_register", globalText.nameInCode);

                listTemplate.add("code_after", "\r\n" + arrayTemplate.render() + "\r\n");

                listCalculation = new CodeFragment(listTemplate.render(), listPointer, variableInfo.type);
            } else if (context.FROM_INPUT() != null) {
                // INPUT TEXT
                if (variableInfo.type.primitive != Type.Primitive.CHAR || variableInfo.type.listDimensions != 1) {
                    errorCollector.add("Problém na riadku " + context.getStart().getLine()
                            + ": Nekompatibilné typy: text nemožno priradiť do " + variableInfo.type.getNameInLLVM());
                    return new CodeFragment();
                }

                if (context.input_type().WORD() != null || context.input_type().LINE() != null) {
                    // create a new list
                    ST listTemplate = templates.getInstanceOf("ListCreation");
                    listTemplate.add("label_id", generateNewLabel());
                    String arrayPointer = generateUniqueRegisterName("array");
                    listTemplate.add("array_register", arrayPointer);
                    String listPointer = generateUniqueRegisterName("list");
                    listTemplate.add("return_register", listPointer);
//                    listTemplate.add("has_value", 0);
//                    listTemplate.add("size", 0);
//                    listTemplate.add("capacity", 4);
//                    listTemplate.add("type", "i8");

                    // add characters
                    ST inputTemplate = templates.getInstanceOf("ReadCharacters");
                    inputTemplate.add("list_register", listPointer);
                    inputTemplate.add("label_id", generateNewLabel());
                    inputTemplate.add("word", context.input_type().WORD() != null ? 1 : 0);

                    listTemplate.add("code_after", "\r\n" + inputTemplate.render() + "\r\n");

                    listCalculation = new CodeFragment(listTemplate.render(), listPointer, variableInfo.type);
                } else {
                    errorCollector.add("Problém na riadku " + context.getStart().getLine()
                            + ": Nekompatibilné typy: sem možno čítať iba text");
                    return new CodeFragment();
                }
            } else {
                // other list variables
                CodeFragment value = visit(context);
                if (!value.type.equals(variableInfo.type)) {
                    errorCollector.add("Problém na riadku " + context.getStart().getLine()
                            + ": Nekompatibilné typy: na pravej strane musí byť " + variableInfo.type.listDimensions + "-rozmerný zoznam.");
                    return new CodeFragment();
                }

                listCalculation = value;
            }

            // whether the place where we are about to write used to contain a list
            ST listTemplate;
            if (shouldGarbageCollect) {
                listTemplate = templates.getInstanceOf("ListReassign");
            } else {
                listTemplate = templates.getInstanceOf("ListAssign");
            }

            listTemplate.add("label_id", generateNewLabel());
            listTemplate.add("layers", variableInfo.type.listDimensions - 1);
            listTemplate.add("new_register", listCalculation.resultRegisterName);
            listTemplate.add("calculate_new", listCalculation);
            listTemplate.add("memory_register", variableInfo.nameInCode);

            return new CodeFragment(listTemplate.render(), variableInfo.nameInCode, variableInfo.type);
        } else {
            // regular integers and bools and whole tables
            CodeFragment value = visit(context);
            if (!value.type.equals(variableInfo.type)) {
                errorCollector.add("Problém na riadku " + context.getStart().getLine()
                        + ": Nekompatibilné typy: premenná vyžaduje typ \"" + variableInfo.type.getNameInLLVM() + "\"");
                return new CodeFragment();
            }

            variableAssignmentTemplate.add("memory_register", variableInfo.nameInCode);
            variableAssignmentTemplate.add("type", variableInfo.type.getNameInLLVM());
            variableAssignmentTemplate.add("compute_value", value);
            variableAssignmentTemplate.add("value_register", value.resultRegisterName);
            return new CodeFragment(variableAssignmentTemplate.render(), variableInfo.nameInCode, variableInfo.type);
        }
    }


    // returns CodeFragment with code and pointer to which one can write
    private CodeFragment declarationAndMaybeAssignment(String name, Type type, C_s_makcenomParser.ExprContext context, int line, CodeFragment calculatedValue) {
        ST declarationTemplate = templates.getInstanceOf("Declaration");

        // check if name unsued
        if (isVariableNameUsed(name)) {
            errorCollector.add("Problém na riadku " + line
                    + ": Názov \"" + name + "\" je už použitý, buďte kreatívnejší pri výbere názvu");
            return new CodeFragment();
        }

        declarationTemplate.add("type", type.getNameInLLVM());

        // allocate space for it
        String registerName = generateUniqueRegisterName(name);
        VariableInfo variableInfo = new VariableInfo(registerName, type);
        variables.peek().put(name, variableInfo);

        declarationTemplate.add("memory_register", registerName);

        String returnRegister = "";

        // calculate initial value of the variable
        if (context != null || calculatedValue != null) {
            CodeFragment assignment = assignment(variableInfo, context, line, calculatedValue, false, false);
            returnRegister = assignment.resultRegisterName;
            declarationTemplate.add("code_after", assignment);
        } else if (type.listDimensions > 0) {
            // create a default value - an empty list
            ST listCreationTemplate = templates.getInstanceOf("ListCreation");
            listCreationTemplate.add("label_id", generateNewLabel());
            listCreationTemplate.add("memory_register", registerName);
            returnRegister = generateUniqueRegisterName("list");
            listCreationTemplate.add("return_register", returnRegister);
            listCreationTemplate.add("store", 1);

            declarationTemplate.add("code_after", listCreationTemplate.render());
        }

        return new CodeFragment(declarationTemplate.render(), returnRegister, variableInfo.type);
    }


    ///////////////////////////////////////////////
    /// Overridden methods
    ///////////////////////////////////////////////

    @Override
    public CodeFragment visitInitial(C_s_makcenomParser.InitialContext ctx) {
        // initialize everything I can
        variables.push(new HashMap<>());
        ST template = templates.getInstanceOf("base");

        // we add the code of each statement
        for (var statement : ctx.statement()) {
            CodeFragment statementCodeFragment = visit(statement);

            if (statementCodeFragment == null) {
                errorCollector.add("Problém na riadku " + statement.getStart().getLine()
                        + ": Syntaktická chyba (viď horeuvedený text v angličtine)");
                return new CodeFragment();
            }
            statementCodeFragment.code = "\t" + statementCodeFragment.code.replaceAll("\n", "\n\t");
            template.add("code", statementCodeFragment + "\r\n");
        }

        // no need to garbage collect, the whole program ends and the OS deals with it.
        // it would be a waste to call free() a bazillion times when the whole program's memory get freed

        // having visited the whole tree, we add all declarations we found
        for (var declaration: declarations) {
            template.add("declarations", declaration+ "\r\n\r\n");
        }

        return new CodeFragment(template.render());
    }

    @Override
    public CodeFragment visitStatementFunction(C_s_makcenomParser.StatementFunctionContext ctx) {
        return visit(ctx.functionDefinition());
    }

    @Override
    public CodeFragment visitStatementWithBody(C_s_makcenomParser.StatementWithBodyContext ctx) {
        return visit(ctx.statementBody());
    }

    @Override
    public CodeFragment visitComment(C_s_makcenomParser.CommentContext ctx) {
        return new CodeFragment("; " + toLowerCaseASCII(ctx.COMMENT().getText()).replaceAll("\n", ""));
    }

    @Override
    public CodeFragment visitNewline(C_s_makcenomParser.NewlineContext ctx) {
        return new CodeFragment();
    }

    @Override
    public CodeFragment visitDeclaration(C_s_makcenomParser.DeclarationContext ctx) {
        return declarationAndMaybeAssignment(ctx.VARIABLE().getText(), new Type(ctx.var_type, errorCollector), ctx.expr(), ctx.start.getLine(), null);
    }

    @Override
    public CodeFragment visitConditional(C_s_makcenomParser.ConditionalContext ctx) {
        ST ifTemplate;

        // select which template to use
        if (ctx.ELSE() != null) {
            ifTemplate = templates.getInstanceOf("IfThenElse");
        } else {
            ifTemplate = templates.getInstanceOf("IfThen");
        }

        CodeFragment logicExpression = checkBool(visit(ctx.logic_expr()), ctx.getStart().getLine());
        ifTemplate.add("compute_boolean", logicExpression);
        ifTemplate.add("boolean_register", logicExpression.resultRegisterName);

        // code to execute if true
        CodeFragment ifTrue = ctx.statementBody(0) != null ? visit(ctx.statementBody(0)) : visit(ctx.block(0));
        ifTemplate.add("if_true", ifTrue);

        // only if ELSE is present
        if (ctx.ELSE() != null) {
            // code to execute if false
            CodeFragment ifFalse = ctx.statementBody(1) != null ? visit(ctx.statementBody(1)) : visit(ctx.block(1));
            ifTemplate.add("if_false", ifFalse);
        }

        ifTemplate.add("label_id", generateNewLabel());

        return new CodeFragment(ifTemplate.render());
    }

    @Override
    public CodeFragment visitForLoop(C_s_makcenomParser.ForLoopContext ctx) {
        ST loopTemplate = templates.getInstanceOf("For");

        String iterationVariableInCode;
        String iterationVariable;

        // either use the user provided one, or make up our own
        if (ctx.varName != null) {
            if (isVariableNameUsed(ctx.varName.getText())) {
                errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                        + ": \"" + ctx.varName.getText() + "\" je už definovaná, vyberte iný názov pre premennú cyklu");
                return new CodeFragment();
            }

            iterationVariable = ctx.varName.getText();
            iterationVariableInCode = generateUniqueRegisterName(ctx.varName.getText());
        } else {
            iterationVariableInCode = generateUniqueRegisterName("iteration_variable");
            // for the variable name, I used the code name, because you can't have variables starting with "%" in Č
            iterationVariable = iterationVariableInCode;
        }

        variables.push(new HashMap<>());
        variables.peek().put(iterationVariable, new VariableInfo(iterationVariableInCode, Type.INT));

        CodeFragment lowerBound = convertToInt(visit(ctx.lower), ctx.getStart().getLine());
        CodeFragment upperBound = convertToInt(visit(ctx.upper), ctx.getStart().getLine());

        loopTemplate.add("iter_memory_register", iterationVariableInCode);
        loopTemplate.add("calculate_values", lowerBound);
        loopTemplate.add("calculate_values", upperBound);
        loopTemplate.add("label_id", generateNewLabel());
        loopTemplate.add("lower", lowerBound.resultRegisterName);
        loopTemplate.add("upper", upperBound.resultRegisterName);

        // generate labels
        String breakLabel = "end_cycle_" + generateNewLabel();
        String continueLabel = "loop_increment_" + generateNewLabel();
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        loopTemplate.add("end_cycle", breakLabel);
        loopTemplate.add("loop_increment", continueLabel);


        CodeFragment code = ctx.statementBody() != null ? visit(ctx.statementBody()) : visit(ctx.block());
        loopTemplate.add("code", code);

        // after visiting everything, pop labels
        breakLabels.pop();
        continueLabels.pop();

        variables.pop();

        return new CodeFragment(loopTemplate.render());
    }

    @Override
    public CodeFragment visitWhileLoop(C_s_makcenomParser.WhileLoopContext ctx) {
        ST loopTemplate = templates.getInstanceOf("While");

        CodeFragment calculation = visit(ctx.condition);

        loopTemplate.add("calculate_values", calculation);
        loopTemplate.add("return_register", calculation.resultRegisterName);
        loopTemplate.add("label_id", generateNewLabel());

        // generate labels
        String breakLabel = "end_cycle_" + generateNewLabel();
        String continueLabel = "loop_" + generateNewLabel();
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        loopTemplate.add("end_cycle", breakLabel);
        loopTemplate.add("loop", continueLabel);


        CodeFragment code = ctx.statementBody() != null ? visit(ctx.statementBody()) : visit(ctx.block());
        loopTemplate.add("code", code);

        // after visiting everything, pop labels
        breakLabels.pop();
        continueLabels.pop();

        return new CodeFragment(loopTemplate.render());
    }



    @Override
    public CodeFragment visitOutput(C_s_makcenomParser.OutputContext ctx) {
        ST outputTemplate;

        CodeFragment codeFragment = visit(ctx.expr());

        // what kind of print to use
        if (codeFragment.type.listDimensions > 0) {
            if (codeFragment.type.listDimensions == 1 && codeFragment.type.primitive == Type.Primitive.CHAR) {
                // String from variable
                // get the array itself
                ST getArrayTemplate = templates.getInstanceOf("GetArrayFromList");
                getArrayTemplate.add("compute_value", codeFragment);
                getArrayTemplate.add("label_id", generateNewLabel());
                String arrayPointer = generateUniqueRegisterName("");
                getArrayTemplate.add("return_register", arrayPointer);
                getArrayTemplate.add("memory_register", codeFragment.resultRegisterName);

                outputTemplate = templates.getInstanceOf("PrintString");
                outputTemplate.add("compute_value", getArrayTemplate.render());
                outputTemplate.add("value_register", arrayPointer);
            } else {
                errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                        + ": Priamo vypisovať zoznamy je možné iba pre zoznamy znakov - teda texty");
                return new CodeFragment();
            }
        } else if (codeFragment.type.primitive == Type.Primitive.INT) {
            outputTemplate = templates.getInstanceOf("PrintNumber");
            outputTemplate.add("compute_value", codeFragment);
            outputTemplate.add("value_register", codeFragment.resultRegisterName);
        } else if (codeFragment.type.primitive == Type.Primitive.CHAR) {
            outputTemplate = templates.getInstanceOf("PrintChar");
            outputTemplate.add("compute_value", codeFragment);
            outputTemplate.add("label_id", generateNewLabel());
            outputTemplate.add("value_register", codeFragment.resultRegisterName);
        } else if (codeFragment.type.primitive == Type.Primitive.BOOL) {
            outputTemplate = templates.getInstanceOf("PrintBoolean");
            outputTemplate.add("compute_value", codeFragment);
            outputTemplate.add("label_id", generateNewLabel());
            outputTemplate.add("value_register", codeFragment.resultRegisterName);
        } else if (ctx.expr().TEXT() != null) {
            // CONSTANT TEXT
            String text = ctx.expr().TEXT().getText().substring(1, ctx.expr().TEXT().getText().length() - 1);

            outputTemplate = templates.getInstanceOf("PrintString");
            outputTemplate.add("value_register", createTextConstant(text).nameInCode);
        } else if (ctx.expr().FROM_INPUT() != null) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": ...naozaj chcete, aby som okamžite vypísal váš vstup? Zožeňte si na to papagája, ja to robiť nejdem");
            return new CodeFragment();
        } else {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Nastala neznáma chyba... Toto autor jazyka nedomyslel :(");
            return new CodeFragment();
        }

        // also add a newline
        if (ctx.AND_PRINT_NEWLINE() != null) {
            outputTemplate.add("newline", 1);
        }
        return new CodeFragment(outputTemplate.render());
    }

    @Override
    public CodeFragment visitPrintNewLine(C_s_makcenomParser.PrintNewLineContext ctx) {
        // literally just output a string, so this is a bit overcomplicated... but whatever
        return new CodeFragment(templates.getInstanceOf("PrintNewline").render());
    }

    @Override
    public CodeFragment visitBreak(C_s_makcenomParser.BreakContext ctx) {
        ST template = templates.getInstanceOf("Branch");
        if (breakLabels.isEmpty()) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Je možné že používaš toto slovo len z frustrácie, ale vedz, že je to kľúčové slovo v cykloch a sem nepatrí");
            return new CodeFragment();
        }

        template.add("where", breakLabels.peek());
        return new CodeFragment(template.render());
    }

    @Override
    public CodeFragment visitContinue(C_s_makcenomParser.ContinueContext ctx) {
        ST template = templates.getInstanceOf("Branch");
        if (continueLabels.isEmpty()) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Čo chceš preskočiť? Tebe preskočilo? Toto sa používa len v cykloch!");
            return new CodeFragment();
        }

        template.add("where", continueLabels.peek());
        return new CodeFragment(template.render());
    }

    private CodeFragment garbageCollectWhenReturning(VariableInfo returning) {
        StringBuilder garbageCollectingCode = new StringBuilder();
        for (var scope: variables) {
            for (var variable: scope.values()) {
                if (variable.type.listDimensions > 0) {
                    // first load the pointer
                    ST loadTemplate = templates.getInstanceOf("GetFromVariable");
                    loadTemplate.add("memory_register", variable.nameInCode);
                    loadTemplate.add("type", variable.type.getNameInLLVM());
                    String uniqueName = generateUniqueRegisterName("");
                    loadTemplate.add("return_register", uniqueName);


                    ST garbageTemplate = templates.getInstanceOf("GarbageCollect");
                    garbageTemplate.add("memory_register", uniqueName);
                    garbageTemplate.add("layers", variable.type.listDimensions - 1);
                    String except = returning.nameInCode;
                    if (except.isBlank() || returning.type.listDimensions == 0) {
                        except = "null";
                    }
                    garbageTemplate.add("except", except);    // do NOT delete this one, which we are returning

                    garbageCollectingCode.append(loadTemplate.render()).append("\r\n")
                            .append(garbageTemplate.render()).append("\r\n");
                }
            }
        }

        return new CodeFragment(garbageCollectingCode.toString());
    }

    @Override
    public CodeFragment visitReturn(C_s_makcenomParser.ReturnContext ctx) {
        ST returnTemplate = templates.getInstanceOf("Return");

        // can only be used in functions
        if (currentFunction == null) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Čo chceš vracať, tu nie sme vo funkcii, na základnú školu sa vráť");
            return new CodeFragment();
        }

        // garbage collect everything from the function

        returnTemplate.add("type", currentFunction.returnType.getNameInLLVM());
        CodeFragment code = visit(ctx.expr());

        returnTemplate.add("compute_value", code);
        returnTemplate.add("value_register", code.resultRegisterName);
        returnTemplate.add("cleanup", garbageCollectWhenReturning(new VariableInfo(code.resultRegisterName, code.type)));

        return new CodeFragment(returnTemplate.render());
    }

    @Override
    public CodeFragment visitReturnNothing(C_s_makcenomParser.ReturnNothingContext ctx) {
        ST returnTemplate = templates.getInstanceOf("ReturnNothing");

        // can only be used in functions
        if (currentFunction == null) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": \"Hotovo\" sa používa len vo funkciách, asi chcete použiť \"Koniec\"");
            return new CodeFragment();
        }

        returnTemplate.add("cleanup", garbageCollectWhenReturning(new VariableInfo("null", Type.VOID)));

        return new CodeFragment(returnTemplate.render());
    }

    @Override
    public CodeFragment visitProcedureCall(C_s_makcenomParser.ProcedureCallContext ctx) {
        if (functions.get(ctx.function_expr().name.getText().toLowerCase()) == null) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Funkcia neexistuje!");
            return new CodeFragment();
        }

        // check whether a non-void function is called
        if (functions.get(ctx.function_expr().name.getText().toLowerCase()).returnType.primitive != Type.Primitive.VOID) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Nie je dovolené ignorovať výsledok funkcií, čo niečo vracajú!");
            return new CodeFragment();
        }
        return visit(ctx.function_expr());
    }

    @Override
    public CodeFragment visitFunction_expr(C_s_makcenomParser.Function_exprContext ctx) {
        // add to template
        ST functionTemplate = templates.getInstanceOf("FunctionCall");

        if (!functions.containsKey(ctx.name.getText().toLowerCase())) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Neznáma funkcia \"" + ctx.name.getText() + "\"");
            return new CodeFragment();
        }

        FunctionInfo functionInfo = functions.get(ctx.name.getText().toLowerCase());

        if (functionInfo.arguments.size() != ctx.expr().size()) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Nesprávny počet argumentov vo funkcii \"" + ctx.name.getText() + "\": Požadovaných"
                    + functionInfo.arguments.size() + ", nájdených " + ctx.expr().size());
            return new CodeFragment();
        }

        List<String> arguments = new ArrayList<>();
        garbageCollectAfterFunction.push(new ArrayList<>());

        // go through the arguments and visit them all
        for (int i = 0; ctx.expr(i) != null; i++) {
            CodeFragment code = visit(ctx.expr(i));
            functionTemplate.add("calculate_arguments", code + "\r\n");

            if (!code.type.equals(functionInfo.arguments.get(i).type)) {
                errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                        + ": Nesprávny typ argumentu číslo " + i + ": Požadovaný typ"
                        + functionInfo.arguments.get(i).type.getNameInLLVM() + ", nájdený " + code.type.getNameInLLVM());
                return new CodeFragment();
            }

            // what to garbage collect right after function call (and assignment)
            if (code.type.listDimensions > 0) {
                garbageCollectAfterFunction.peek().add(new VariableInfo(code.resultRegisterName, code.type));
            }

            arguments.add(functionInfo.arguments.get(i).type.getNameInLLVM() + " " + code.resultRegisterName);
        }

        functionTemplate.add("name", functionInfo.nameInCode);
        functionTemplate.add("return_type", functionInfo.returnType.getNameInLLVM());
        functionTemplate.add("arguments", String.join(", ", arguments));



        // if not void, we add a register which will hold the result
        if (functionInfo.returnType.primitive != Type.Primitive.VOID) {
            functionTemplate.add("is_not_void", 1);
            String returnRegister = generateUniqueRegisterName("");
            functionTemplate.add("destination", returnRegister);

            return new CodeFragment(functionTemplate.render(), returnRegister, functionInfo.returnType);
        } else {
            return new CodeFragment(functionTemplate.render());
        }
    }

    @Override
    public CodeFragment visitAssignment(C_s_makcenomParser.AssignmentContext ctx) {
        CodeFragment left = visit(ctx.id());
        CodeFragment assignment = new CodeFragment(left.code + "\r\n" + assignment(
                new VariableInfo(left.resultRegisterName, left.type),
                ctx.expr(),
                ctx.start.getLine(),
                null,
                ctx.LOGIC_ASSIGNMENT() != null,
                true));

        // after every function call, garbage collect all arguments
        // because inside the function we incremented the reference count
        StringBuilder garbageCollectingCode = new StringBuilder();

        if (justExitedFunction) {
            for (VariableInfo register : garbageCollectAfterFunction.peek()) {
                ST garbageTemplate = templates.getInstanceOf("GarbageCollect");
                garbageTemplate.add("memory_register", register.nameInCode);
                garbageTemplate.add("layers", register.type.listDimensions - 1);
                garbageTemplate.add("except", "null");    // do NOT delete this one, which we are returning


                garbageCollectingCode.append(garbageTemplate.render()).append("\r\n");
            }
            garbageCollectAfterFunction.pop();
            justExitedFunction = false;
        }


        return new CodeFragment(assignment + garbageCollectingCode.toString());
    }

    @Override
    public CodeFragment visitFunctionDefinition(C_s_makcenomParser.FunctionDefinitionContext ctx) {
        // no functions inside one another!!
        if (currentFunction != null) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Nepodporujeme vnorené funkcie! Dajte funkciu \"" + ctx.name.getText() + "\" inam");
            return new CodeFragment();
        }

        // add to template
        ST functionTemplate = templates.getInstanceOf("FunctionDefinition");

        // firstly, gather all the needed info
        FunctionInfo functionInfo = new FunctionInfo();
        functionInfo.nameInCode = toLowerCaseASCII(ctx.name.getText()) + generateNewLabel();

        // returning type
        functionInfo.returnType = new Type(ctx.returning, errorCollector);

        // add new scope and hide existing variables
        // when in a function definition, hide all other variables
        Stack<HashMap<String, VariableInfo>> hiddenVariables = variables;
        variables = new Stack<>();
        variables.push(new HashMap<>());

        // go through the arguments and add them to the scope
        // from 1 because we ignore the name of the function
        for (int i = 1; ctx.VARIABLE(i) != null; i++) {
            // but types start from 0 if the function doesn't return anything
            int from = functionInfo.returnType.primitive == Type.Primitive.VOID ? 1 : 0;

            VariableInfo argument = new VariableInfo(
                    generateUniqueRegisterName(ctx.VARIABLE(i).getText()),
                    new Type(ctx.type(i - from), errorCollector));

            // create a new variable for every argument
            CodeFragment variableFromArgument = declarationAndMaybeAssignment(
                    ctx.VARIABLE(i).getText(),
                    argument.type,
                    null,
                    ctx.start.getLine(),
                    new CodeFragment("", argument.nameInCode, argument.type));

            variableFromArgument.code = "\t" + variableFromArgument.code.replaceAll("\n", "\n\t");

            // add it to the code of the function
            functionTemplate.add("code", variableFromArgument);

            functionInfo.arguments.add(argument);
        }

        // put into the registry for functions
        functions.put(ctx.name.getText().toLowerCase(), functionInfo);

        // and set as the current function
        currentFunction = functionInfo;

        functionTemplate.add("name", functionInfo.nameInCode);
        functionTemplate.add("return_type", functionInfo.returnType.getNameInLLVM());

        // make a nice looking list of arguments
        functionTemplate.add("arguments", functionInfo.arguments.stream()
                .map(variableInfo -> variableInfo.type.getNameInLLVM() + " " + variableInfo.nameInCode)
                .collect(Collectors.joining(", ")));

        // there must be at least one return statement in this scope
        // otherwise memory leaks from lists could happen
        if (ctx.statement().stream().noneMatch(statement ->
                statement instanceof C_s_makcenomParser.StatementWithBodyContext
                && (((C_s_makcenomParser.StatementWithBodyContext) statement).statementBody() instanceof C_s_makcenomParser.ReturnContext
                || ((C_s_makcenomParser.StatementWithBodyContext) statement).statementBody() instanceof C_s_makcenomParser.ReturnNothingContext)
        )) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Funkcia \"" + ctx.name.getText() + "\" musí mať aspoň jeden NEVNORENÝ príkaz na návrat - teda, mimo cyklov a podmienok. " +
                    "Potrebujeme nejak zabezpečiť, že funkcia vždy vráti.");
            return new CodeFragment();
        }

        // add code inside the function
        // we add the code of each statement
        for (var statement : ctx.statement()) {
            CodeFragment statCodeFragment = visit(statement);
            functionTemplate.add("code", "\t"
                    + statCodeFragment.code.replaceAll("\n", "\n\t")
                    + "\r\n");
        }

        // pop the scope and unset the current function
        variables = hiddenVariables;
        currentFunction = null;
        justExitedFunction = true;

        // DON'T actually return the function definition! Just save it
        declarations.add(new CodeFragment(functionTemplate.render()));
        return new CodeFragment();
    }

    @Override
    public CodeFragment visitBlock(C_s_makcenomParser.BlockContext ctx) {
        // all variables in this block get put in a new layer, so they can be disregarded after the end of the block
        variables.push(new HashMap<>());
        ST template = templates.getInstanceOf("block");

        // we add the code of each statement
        for (var statement : ctx.statement()) {
            CodeFragment statCodeFragment = visit(statement);
            template.add("code", statCodeFragment + "\r\n");
        }

        // this forgets all variables declared in the block
        Collection<VariableInfo> exitingScope = variables.pop().values();

        // after scope ends, garbage collect all variables
        StringBuilder garbageCollectingCode = new StringBuilder();

        for (VariableInfo register: exitingScope) {
            if (register.type.listDimensions > 0) {
                ST garbageTemplate = templates.getInstanceOf("GarbageCollect");
                garbageTemplate.add("memory_register", register.nameInCode);
                garbageTemplate.add("layers", register.type.listDimensions - 1);
                garbageTemplate.add("except", "null");

                garbageCollectingCode.append(garbageTemplate.render()).append("\r\n");
            }
        }

        return new CodeFragment("\t"
                + (template.render() + "\r\n" + garbageCollectingCode)
                .replaceAll("\n", "\n\t"));
    }

    @Override
    public CodeFragment visitArrayElement(C_s_makcenomParser.ArrayElementContext ctx) {
        String arrayName = ctx.array.getText();

        // get the location of the variable, so stuff can be stored inside
        VariableInfo info = getVariableInfo(arrayName, ctx.getStart().getLine());

        // copy of the type
        Type innerType = Type.copyOf(info.type);

        // different approaches for static and dynamic arrays
        if (!info.type.tableLengths.isEmpty()) {
        // TABLE
            ST tableTemplate = templates.getInstanceOf("LocateArrayElement");
            tableTemplate.add("memory_register", info.nameInCode);
            tableTemplate.add("index_registers", "i32 0, ");  // because we aren't in an array of arrays
            tableTemplate.add("type", info.type.getNameInLLVM());


            // index
            if (ctx.index != null) {
                // simple variable
                CodeFragment indexCode = loadVariableIntoRegister(getVariableInfo(ctx.index.getText(), ctx.getStart().getLine()));
                tableTemplate.add("calculate_index", indexCode);
                tableTemplate.add("index_registers", "i32 " + indexCode.resultRegisterName);

                // drop down one dimension
                innerType.tableLengths.removeFirst();
            } else {
                // expression, possibly multidimensional
                // visit all
                List<CodeFragment> indexCodes = ctx.num_expr().stream().map(this::visit).toList();

                tableTemplate.add("calculate_index", String.join("\r\n", indexCodes.stream().map(CodeFragment::toString).toList()));
                tableTemplate.add("index_registers", String.join(", ", indexCodes.stream().map(codeFragment -> "i32 " + codeFragment.resultRegisterName).toList()));

                // drop down the correct number of dimensions
                ctx.num_expr().forEach(x -> innerType.tableLengths.removeFirst());
            }

            String uniqueRegister = generateUniqueRegisterName("");
            tableTemplate.add("return_register", uniqueRegister);


            return new CodeFragment(tableTemplate.render(), uniqueRegister, innerType);


        } else if (info.type.listDimensions > 0) {
        // LIST

            // index
            List<CodeFragment> indexes;

            if (ctx.index != null) {
                // simple variable
                indexes = List.of(loadVariableIntoRegister(getVariableInfo(ctx.index.getText(), ctx.getStart().getLine())));
            } else {
                // expression, possibly multidimensional
                // visit all
                indexes = ctx.num_expr().stream().map(this::visit).toList();
            }

            // do multiple list accesses
            String lastPointer = info.nameInCode;
            StringBuilder elementLocation = new StringBuilder();
            for (CodeFragment indexCode : indexes) {
                // drop down the correct number of dimensions
                innerType.listDimensions--;

                ST getListElementTemplate = templates.getInstanceOf("GetListElement");
                getListElementTemplate.add("calculate_index", indexCode);
                getListElementTemplate.add("index", indexCode.resultRegisterName);
                getListElementTemplate.add("type", innerType.getNameInLLVM());
                getListElementTemplate.add("label_id", generateNewLabel());
                getListElementTemplate.add("memory_register", lastPointer);

                // now make a new register for the inner array
                lastPointer = generateUniqueRegisterName("");
                getListElementTemplate.add("return_register", lastPointer);

                elementLocation.append(getListElementTemplate.render()).append("\r\n");
            }

            // in lastPointer is the pointer to the correct place
            // just gluing these together is enough
            return new CodeFragment(elementLocation.toString(), lastPointer, innerType);
        } else {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Indexovať sa dá do tabuľky, zoznamu a textu, nie do " + info.type.getNameInLLVM());
            return new CodeFragment();
        }
    }

    @Override
    public CodeFragment visitAddElement(C_s_makcenomParser.AddElementContext ctx) {
        CodeFragment list = visit(ctx.id());

        if (list.type.listDimensions == 0) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Pridávať prvky je možné len do zoznamu a textu, nie do " + list.type.getNameInLLVM());
            return new CodeFragment();
        }

        ST increaseListSizeTemplate = templates.getInstanceOf("IncreaseSize");
        increaseListSizeTemplate.add("calculate_value", list);
        increaseListSizeTemplate.add("memory_register", list.resultRegisterName);
        increaseListSizeTemplate.add("label_id", generateNewLabel());
        String uniqueRegister = generateUniqueRegisterName("");
        increaseListSizeTemplate.add("return_register", uniqueRegister);

        Type innerType = Type.copyOf(list.type);
        innerType.listDimensions--;

        increaseListSizeTemplate.add("type", innerType.getNameInLLVM());

        CodeFragment assign = assignment(
                new VariableInfo(uniqueRegister, innerType),
                ctx.expr(),
                ctx.getStart().getLine(),
                null,
                false,
                false
                );

        increaseListSizeTemplate.add("code_after", assign);

        return new CodeFragment(increaseListSizeTemplate.render());
    }

    @Override
    public CodeFragment visitRemoveElement(C_s_makcenomParser.RemoveElementContext ctx) {
        CodeFragment list = visit(ctx.id());

        if (list.type.listDimensions == 0) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Odoberať prvky je možné len zo zoznamu a textu, nie z " + list.type.getNameInLLVM());
            return new CodeFragment();
        }

        ST decreaseListSizeTemplate = templates.getInstanceOf("DecreaseSize");
        decreaseListSizeTemplate.add("calculate_value", list);
        decreaseListSizeTemplate.add("memory_register", list.resultRegisterName);
        decreaseListSizeTemplate.add("label_id", generateNewLabel());

        Type innerType = Type.copyOf(list.type);
        innerType.listDimensions--;

        decreaseListSizeTemplate.add("type", innerType.getNameInLLVM());

        return new CodeFragment(decreaseListSizeTemplate.render());
    }

    @Override
    public CodeFragment visitVariable(C_s_makcenomParser.VariableContext ctx) {
        VariableInfo info = getVariableInfo(ctx.VARIABLE().getText(), ctx.getStart().getLine());
        return new CodeFragment("", info.nameInCode, info.type);
    }

    @Override
    public CodeFragment visitExpr(C_s_makcenomParser.ExprContext ctx) {
        if (ctx.num_expr() != null) {
            return visit(ctx.num_expr());
        } else if (ctx.logic_expr() != null) {
            return visit(ctx.logic_expr());
        } else if (ctx.CHARACTER() != null) {
            // all chars should be of the format "'c'", so we need the character on position 1
            int character = ctx.CHARACTER().getText().charAt(1);

            String text = ctx.CHARACTER().getText();

            character = switch (text) {
                case "'\\0'" -> 0;
                case "'\\n'" -> 0x0A;
                case "'\\t'" -> 0x09;
                case "'\\\\'" -> 0x5C;
                default -> character;
            };

            // check whether the character fits in i8 and whether it isn't something more complicated (like a Chinese character)
            if (character > 0xFF) {
                errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                        + ": \"" + ctx.CHARACTER().getText() + "\" tu nemožno použiť, treba len písmenko bez diakritiky :/");
                return new CodeFragment();
            }

            // once again, we convert the character to a number and pass it directly as an "output register"
            return new CodeFragment("", Integer.toString(character), Type.CHAR);
        } else if (ctx.function_expr() != null) {
            return visit(ctx.function_expr());
        } else if (ctx.TEXT() != null) {
            // this is correct, all the heavy lifting is done by Output
            return new CodeFragment();
        } else if (ctx.FROM_INPUT() != null) {
            // input
            String uniqueRegister = generateUniqueRegisterName("input");

            if (ctx.input_type().INT() != null) {
                ST template = templates.getInstanceOf("GetInt");
                template.add("return_register", uniqueRegister);
                return new CodeFragment(template.render(), uniqueRegister, Type.INT);
            } else if (ctx.input_type().CHAR() != null) {
                ST template = templates.getInstanceOf("GetChar");
                template.add("return_register", uniqueRegister);
                template.add("label_id", generateNewLabel());
                return new CodeFragment(template.render(), uniqueRegister, Type.CHAR);
            } else {
                // this is correct, all the heavy lifting is done by Output
                return new CodeFragment();
            }
        } else {
            // Arrays
            return visit(ctx.array_expr());
        }
    }

    @Override
    public CodeFragment visitExprParen(C_s_makcenomParser.ExprParenContext ctx) {
        return visit(ctx.num_expr());
    }

    @Override
    public CodeFragment visitIdentifier(C_s_makcenomParser.IdentifierContext ctx) {
        CodeFragment place = visit(ctx.id());
        CodeFragment loaded = loadVariableIntoRegister(new VariableInfo(place.resultRegisterName, place.type));
        return new CodeFragment(place.code + "\r\n" + loaded.code, loaded.resultRegisterName, loaded.type);
    }

    @Override
    public CodeFragment visitNegative(C_s_makcenomParser.NegativeContext ctx) {
        ST negativeTemplate = templates.getInstanceOf("Negative");

        CodeFragment inner = convertToInt(visit(ctx.num_expr()), ctx.getStart().getLine());
        negativeTemplate.add("compute_value", inner);
        negativeTemplate.add("value_register", inner.resultRegisterName);
        String uniqueName = generateUniqueRegisterName("");
        negativeTemplate.add("return_register", uniqueName);

        return new CodeFragment(negativeTemplate.render(), uniqueName, Type.INT);
    }

    @Override
    public CodeFragment visitNumber(C_s_makcenomParser.NumberContext ctx) {
        // hilarious hack: we place the values as the "register", because it works with our templates :P
        return new CodeFragment("", ctx.NUMBER().getText(), Type.INT);
    }

    private CodeFragment convertToInt(CodeFragment code, int line) {
        // convert characters to ints
        switch (code.type.primitive) {
            case Type.Primitive.CHAR -> {
                ST template = templates.getInstanceOf("CharToInt");
                template.add("compute_value", code);
                template.add("value_register", code.resultRegisterName);
                String returnRegister = generateUniqueRegisterName("converted");
                template.add("return_register", returnRegister);

                return new CodeFragment(template.render(), returnRegister, Type.INT);
            }
            case Type.Primitive.INT -> {
                // no need to do anything
                return code;
            }
            default -> {
                errorCollector.add("Problém na riadku " + line
                        + ": Tu sú operácie s celými číslami (prípadne znakmi), ale našli sme tu typ " + code.type.getNameInLLVM());
                return new CodeFragment();
            }
        }
    }

    @Override
    public CodeFragment visitBinaryOperation(C_s_makcenomParser.BinaryOperationContext ctx) {
        ST BinOpTemplate = templates.getInstanceOf("BinOp");
        BinOpTemplate.add("type", "i32");

        // find out which operation we're doing
        String operator = switch(ctx.op.getType()) {
            case C_s_makcenomParser.MULTIPLICATION -> "mul";
            case C_s_makcenomParser.DIVISION -> "sdiv";
            case C_s_makcenomParser.MODULO -> "srem";
            case C_s_makcenomParser.ADDITION -> "add";
            case C_s_makcenomParser.SUBTRACTION -> "sub";
            default -> "";
        };

        // not sure if an unknown operator can happen, but I trust that someone will make this error appear
        if (operator.isEmpty()) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Neznáma matematická operácia... netuším, ako sa vám to podarilo");
            return new CodeFragment();
        }

        BinOpTemplate.add("instruction", operator);

        CodeFragment left = convertToInt(visit(ctx.left), ctx.getStart().getLine());
        CodeFragment right = convertToInt(visit(ctx.right), ctx.getStart().getLine());

        BinOpTemplate.add("compute_left", left);
        BinOpTemplate.add("compute_right", right);
        BinOpTemplate.add("left_register", left.resultRegisterName);
        BinOpTemplate.add("right_register", right.resultRegisterName);

        String uniqueName = generateUniqueRegisterName("");
        BinOpTemplate.add("return_register", uniqueName);

        return new CodeFragment(BinOpTemplate.render(), uniqueName, Type.INT);
    }

    @Override
    public CodeFragment visitArraySize(C_s_makcenomParser.ArraySizeContext ctx) {
        CodeFragment variable = visit(ctx.id());
        if (variable.type.listDimensions == 0) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Dĺžku je možné brať len zoznamov a uložených textov");
            return new CodeFragment();
        }

        ST getSizeTemplate = templates.getInstanceOf("GetListSize");
        getSizeTemplate.add("calculate_value", variable);
        getSizeTemplate.add("memory_register", variable.resultRegisterName);
        String uniqueRegister = generateUniqueRegisterName("string_length");
        getSizeTemplate.add("return_register", uniqueRegister);
        getSizeTemplate.add("label_id", generateNewLabel());

        return new CodeFragment(getSizeTemplate.render(), uniqueRegister, Type.INT);
    }

    @Override
    public CodeFragment visitLogicalValue(C_s_makcenomParser.LogicalValueContext ctx) {
        // we use the same trick of just putting the value as the "output register"
        return new CodeFragment("", ctx.val.getType() == C_s_makcenomParser.FALSE ? "0" : "1", Type.BOOL);
    }

    private CodeFragment checkBool(CodeFragment code, int line) {
        if (code.type.primitive != Type.Primitive.BOOL) {
            errorCollector.add("Problém na riadku " + line
                    + ": Tu sú operácie s celými číslami (prípadne znakmi), ale našli sme tu typ " + code.type.getNameInLLVM());
            return new CodeFragment();
        } else {
            return code;
        }
    }

    @Override
    public CodeFragment visitNegation(C_s_makcenomParser.NegationContext ctx) {
        ST negationTemplate = templates.getInstanceOf("Negation");

        CodeFragment inner = checkBool(visit(ctx.logic_expr()), ctx.getStart().getLine());
        negationTemplate.add("compute_value", inner);
        negationTemplate.add("value_register", inner.resultRegisterName);
        String uniqueName = generateUniqueRegisterName("");
        negationTemplate.add("return_register", uniqueName);

        return new CodeFragment(negationTemplate.render(), uniqueName, Type.BOOL);
    }

    @Override
    public CodeFragment visitBinaryRelationOperation(C_s_makcenomParser.BinaryRelationOperationContext ctx) {
        ST BinOpTemplate = templates.getInstanceOf("RelationBinOp");

        // find out which operation we're doing
        String operator = switch(ctx.op.getType()) {
            case C_s_makcenomParser.LESS_THAN -> "slt";
            case C_s_makcenomParser.LESS_THAN_OR_EQUAL -> "sle";
            case C_s_makcenomParser.MORE_THAN -> "sgt";
            case C_s_makcenomParser.MORE_THAN_OR_EQUAL -> "sge";
            case C_s_makcenomParser.EQUALS -> "eq";
            case C_s_makcenomParser.NOT_EQUALS -> "ne";
            default -> "";
        };

        // not sure if an unknown operator can happen, but I trust that someone will make this error appear
        if (operator.isEmpty()) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Neznáme porovnanie... netuším, ako sa vám to podarilo");
            return new CodeFragment();
        }

        BinOpTemplate.add("instruction", operator);

        CodeFragment left = convertToInt(visit(ctx.left), ctx.getStart().getLine());
        CodeFragment right = convertToInt(visit(ctx.right), ctx.getStart().getLine());

        BinOpTemplate.add("type", "i32");


        BinOpTemplate.add("compute_left", left);
        BinOpTemplate.add("compute_right", right);
        BinOpTemplate.add("left_register", left.resultRegisterName);
        BinOpTemplate.add("right_register", right.resultRegisterName);

        String uniqueName = generateUniqueRegisterName("");
        BinOpTemplate.add("return_register", uniqueName);

        return new CodeFragment(BinOpTemplate.render(), uniqueName, Type.BOOL);
    }

    @Override
    public CodeFragment visitBinaryLogicOperation(C_s_makcenomParser.BinaryLogicOperationContext ctx) {
        ST BinOpTemplate = templates.getInstanceOf("BinOp");
        BinOpTemplate.add("type", "i1");

        // find out which operation we're doing
        String operator = switch(ctx.op.getType()) {
            case C_s_makcenomParser.AND -> "and";
            case C_s_makcenomParser.OR -> "or";
            case C_s_makcenomParser.XOR -> "xor";
            default -> "";
        };

        // not sure if an unknown operator can happen, but I trust that someone will make this error appear
        if (operator.isEmpty()) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Neznáma logická operácia... môžeme povedať, že je to... nelogické :P");
            return new CodeFragment();
        }

        BinOpTemplate.add("instruction", operator);

        CodeFragment left = checkBool(visit(ctx.left), ctx.getStart().getLine());
        CodeFragment right = checkBool(visit(ctx.right), ctx.getStart().getLine());

        BinOpTemplate.add("compute_left", left);
        BinOpTemplate.add("compute_right", right);
        BinOpTemplate.add("left_register", left.resultRegisterName);
        BinOpTemplate.add("right_register", right.resultRegisterName);

        String uniqueName = generateUniqueRegisterName("");
        BinOpTemplate.add("return_register", uniqueName);

        return new CodeFragment(BinOpTemplate.render(), uniqueName, Type.BOOL);
    }

    @Override
    public CodeFragment visitLogicIdentifier(C_s_makcenomParser.LogicIdentifierContext ctx) {
        CodeFragment place = visit(ctx.id());
        CodeFragment loaded = loadVariableIntoRegister(new VariableInfo(place.resultRegisterName, place.type));
        return new CodeFragment(place.code + "\r\n" + loaded.code, loaded.resultRegisterName, loaded.type);
    }

    @Override
    public CodeFragment visitLogicParen(C_s_makcenomParser.LogicParenContext ctx) {
        return visit(ctx.logic_expr());
    }

    @Override
    public CodeFragment visitArray_expr(C_s_makcenomParser.Array_exprContext ctx) {
        // this is left unused, since the compiler deals with arrays differently based on
        // whether the destination is a static or a dynamic array
        // this error will be dealt with elsewhere
        errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                + ": Nie je dovolené vracať novovytvorené zoznamy, je nutné ich najprv uložiť do premennej");
        return new CodeFragment();
    }
}

package eo.cxivo;

import org.stringtemplate.v4.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;


public class LanguageVisitor extends C_s_makcenomBaseVisitor<CodeFragment> {
    STGroup templates = new STGroupFile("helper-libs/basics.stg");

    // new HashSet get pushed with every new scope
    private final Stack<HashMap<String, VariableInfo>> variables = new Stack<>();
    private final ErrorCollector errorCollector;
    private final List<CodeFragment> declarations = new ArrayList<>();
    private final HashMap<String, FunctionInfo> functions = new HashMap<>();
    private FunctionInfo currentFunction = null;

    private int registerIndex = 0;
    private int labelIndex = 0;

    // from https://www.baeldung.com/java-remove-accents-from-text
    static String toLowerCaseASCII(String input) {
        return Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    }

    private String generateUniqueRegisterName(String originalName) {
        if (originalName.isEmpty()) {
            return "%reg_" + registerIndex++;
        } else {
            return "%" + toLowerCaseASCII(originalName) + "_" + registerIndex++;
        }
    }

    private String generateNewLabel() {
        return Integer.toString(labelIndex++);
    }


    private boolean isVariableNameUsed(String name) {
        return variables.stream().anyMatch(map -> map.containsKey(name.toLowerCase()));
    }

    private VariableInfo getVariableInfo(String name) {
        // search in defined variables
        for (HashMap<String, VariableInfo> map: variables) {
            if (map.containsKey(name.toLowerCase())) {
                return map.get(name.toLowerCase());
            }
        }
        return null;
    }

    public LanguageVisitor(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    ///////////////////////////////////////////////
    /// Overriden methods
    ///////////////////////////////////////////////

    @Override
    public CodeFragment visitInitial(C_s_makcenomParser.InitialContext ctx) {
        // initialize everything I can
        variables.push(new HashMap<>());
        ST template = templates.getInstanceOf("base");

        // we add the code of each statement
        for (var statement : ctx.statement()) {
            CodeFragment statCodeFragment = visit(statement);
            template.add("code", statCodeFragment + "\r\n");
        }

        // having visited the whole tree, we add all declarations we found
        for (var declaration: declarations) {
            template.add("declarations", declaration);
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
        return new CodeFragment();
    }

    @Override
    public CodeFragment visitNewline(C_s_makcenomParser.NewlineContext ctx) {
        return new CodeFragment();
    }


/*    private String formatTableConstant(C_s_makcenomParser.Array_exprContext context) {
        // check if the user isn't pulling dirty tricks
        if (ctx.expr().array_expr() == null) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Do tabuľky je možné priradiť iba tabuľku, nič iné, toto nie je JavaScript");
            return new CodeFragment();
        }

        ctx.expr().array_expr()
    }*/

    @Override
    public CodeFragment visitDeclaration(C_s_makcenomParser.DeclarationContext ctx) {
        ST declarationTemplate = templates.getInstanceOf("DeclarationAndAssignment");

        // check if name unsued
        String variableName = ctx.VARIABLE().getText();
        if (isVariableNameUsed(variableName)) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Názov \"" + variableName + "\" je už použitý, buďte kreatívnejší pri výbere názvu");
            return new CodeFragment();
        }

        // get the type
        Type type = new Type(ctx.var_type, errorCollector);
        declarationTemplate.add("type", type.getNameInLLVM());


        // allocate space for it TODO only primitives
        String registerName = generateUniqueRegisterName(variableName);
        variables.peek().put(variableName, new VariableInfo(registerName, type));

        declarationTemplate.add("memory_register", registerName);

        // calculate initial value of the variable
        if (ctx.expr() != null) {
            if (type.type.getFirst() == Type.Types.TABLE) {
                    errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                            + ": Do tabuľky nemožno nič priraďovať, autorom jazyka sa to nechcelo implementovať");
                    return new CodeFragment();
            } else {
                // regular integers and bools
                CodeFragment calculation = visit(ctx.expr());
                declarationTemplate.add("has_value", 1);
                declarationTemplate.add("compute_value", calculation.toString());
                declarationTemplate.add("value_register", calculation.resultRegisterName);
            }
        }

        return new CodeFragment(declarationTemplate.render());
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

        CodeFragment logicExpression = visit(ctx.logic_expr());
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
        return null;
    }

    @Override
    public CodeFragment visitWhileLoop(C_s_makcenomParser.WhileLoopContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitInput(C_s_makcenomParser.InputContext ctx) {

        return null;
    }

    @Override
    public CodeFragment visitOutput(C_s_makcenomParser.OutputContext ctx) {
        ST outputTemplate = null;

        CodeFragment codeFragment = visit(ctx.expr());

        // what kind of print to use
        if (ctx.expr().num_expr() != null) {
            outputTemplate = templates.getInstanceOf("PrintNumber");
            outputTemplate.add("compute_value", codeFragment);
            outputTemplate.add("value_register", codeFragment.resultRegisterName);
        } else if (ctx.expr().CHARACTER() != null) {
            outputTemplate = templates.getInstanceOf("PrintNumber");
            outputTemplate.add("compute_value", codeFragment);
            outputTemplate.add("value_register", codeFragment.resultRegisterName);
        } // TODO

        assert outputTemplate != null;

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
    public CodeFragment visitEndProgram(C_s_makcenomParser.EndProgramContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitBreak(C_s_makcenomParser.BreakContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitContinue(C_s_makcenomParser.ContinueContext ctx) {
        return null;
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

        returnTemplate.add("type", currentFunction.returnType.getNameInLLVM());
        CodeFragment code = visit(ctx.expr());

        returnTemplate.add("compute_value", code);
        returnTemplate.add("value_register", code.resultRegisterName);

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

        return new CodeFragment(returnTemplate.render());
    }

    @Override
    public CodeFragment visitProcedureCall(C_s_makcenomParser.ProcedureCallContext ctx) {
       return visit(ctx.funcion_expr());
    }

    @Override
    public CodeFragment visitFuncion_expr(C_s_makcenomParser.Funcion_exprContext ctx) {
        // add to template
        ST functionTemplate = templates.getInstanceOf("FunctionCall");

        if (!functions.containsKey(ctx.name.getText().toLowerCase())) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Neznáma funkcia \"" + ctx.name.getText() + "\"");
            return new CodeFragment();
        }

        FunctionInfo functionInfo = functions.get(ctx.name.getText().toLowerCase());
        List<String> arguments = new ArrayList<>();

        // go through the arguments and visit them all
        for (int i = 0; ctx.expr(i) != null; i++) {
            CodeFragment code = visit(ctx.expr(i));
            functionTemplate.add("calculate_arguments", code + "\r\n");

            arguments.add(functionInfo.arguments.get(i).type.getNameInLLVM() + " " + code.resultRegisterName);
        }

        functionTemplate.add("name", functionInfo.nameInCode);
        functionTemplate.add("return_type", functionInfo.returnType.getNameInLLVM());
        functionTemplate.add("arguments", String.join(", ", arguments));

        // if not void, we add a register which will hold the result
        if (functionInfo.returnType.type.getFirst() != Type.Types.VOID) {
            functionTemplate.add("is_not_void", 1);
            String returnRegister = generateUniqueRegisterName("");
            functionTemplate.add("destination", returnRegister);

            return new CodeFragment(functionTemplate.render(), returnRegister);
        } else {
            return new CodeFragment(functionTemplate.render());
        }
    }

    @Override
    public CodeFragment visitVariableAssignment(C_s_makcenomParser.VariableAssignmentContext ctx) {
        ST variableAssignmentTemplate = templates.getInstanceOf("VariableAssignment");

        // check if name is defined
        String variableName = ctx.VARIABLE().getText();
        if (!isVariableNameUsed(variableName)) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Neznáma premenná \"" + variableName + "\" (inak, v Č treba pred použitím deklarovať)");
            return new CodeFragment();
        }

        // get the location of the variable, so stuff can be stored inside
        VariableInfo info = getVariableInfo(variableName);
        assert info != null;
        variableAssignmentTemplate.add("memory_register", info.nameInCode);
        variableAssignmentTemplate.add("type", info.type.getNameInLLVM());

        // code for computing the value
        CodeFragment value;
        if (ctx.logic_expr() != null) {
            value = visit(ctx.logic_expr());
        } else {
            value = visit(ctx.expr());
        }

        variableAssignmentTemplate.add("compute_value", value);
        variableAssignmentTemplate.add("value_register", value.resultRegisterName);

        return new CodeFragment(variableAssignmentTemplate.render());
    }

    @Override
    public CodeFragment visitArrayElementAssignment(C_s_makcenomParser.ArrayElementAssignmentContext ctx) {
        String arrayName = ctx.array.getText();
        if (!isVariableNameUsed(arrayName)) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Neznáma premenná \"" + arrayName + "\" (inak, v Č treba pred použitím deklarovať)");
            return new CodeFragment();
        }

        // get the location of the variable, so stuff can be stored inside
        VariableInfo info = getVariableInfo(arrayName);
        assert info != null;

        // different approaches for static and dynamic arrays
        if (info.type.type.getFirst() == Type.Types.TABLE) {
            ST tableTemplate = templates.getInstanceOf("SetTableElement");
            tableTemplate.add("memory_register", info.nameInCode);
            tableTemplate.add("type", info.type.getNameInLLVM());
            tableTemplate.add("base_type", info.type.getBaseTypeNameInLLVM());
            tableTemplate.add("label_id", generateNewLabel());

            // input
            CodeFragment input;
            if (ctx.logic_expr() != null) {
                input = visit(ctx.logic_expr());
            } else {
                input = visit(ctx.expr());
            }

            tableTemplate.add("calculate_value", input);
            tableTemplate.add("value_register", input.resultRegisterName);

            // index
            if (ctx.index != null) {
                // simple variable
                CodeFragment indexCode = visitVariableFromMorePlaces(ctx.index.getText(), ctx.getStart().getLine());
                tableTemplate.add("calculate_index", indexCode);
                tableTemplate.add("index_registers", "i32 " + indexCode.resultRegisterName);
            } else {
                // expression, possibly multidimensional
                // visit all
                List<CodeFragment> indexCodes = ctx.num_expr().stream().map(this::visit).toList();

                tableTemplate.add("calculate_index", String.join("\r\n", indexCodes.stream().map(CodeFragment::toString).toList()));
                tableTemplate.add("index_registers", String.join(", ", indexCodes.stream().map(codeFragment -> "i32 " + codeFragment.resultRegisterName).toList()));
            }

            return new CodeFragment(tableTemplate.render());
        } else {
            return null;
        }
    }

    @Override
    public CodeFragment visitCharOfTextAssignment(C_s_makcenomParser.CharOfTextAssignmentContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitFunctionDefinition(C_s_makcenomParser.FunctionDefinitionContext ctx) {
        // add to template
        ST functionTemplate = templates.getInstanceOf("FunctionDefinition");

        // firstly, gather all the needed info
        FunctionInfo functionInfo = new FunctionInfo();
        functionInfo.nameInCode = toLowerCaseASCII(ctx.name.getText()) + generateNewLabel();

        // returning type
        functionInfo.returnType = new Type(ctx.returning, errorCollector);

        // add new scope
        variables.push(new HashMap<>());

        // go through the arguments and add them to the scope
        // from 1 because we ignore the name of the function
        for (int i = 1; ctx.VARIABLE(i) != null; i++) {
            // but types start from 0 if the function doesn't return anything
            int from = functionInfo.returnType.type.getFirst() == Type.Types.VOID ? 1 : 0;

            VariableInfo argument = new VariableInfo(
                    generateUniqueRegisterName(ctx.VARIABLE(i).getText()),
                    new Type(ctx.type(i - from), errorCollector));

            // create a new variable for every argument
            VariableInfo createdVariable = new VariableInfo(argument.nameInCode + "_var", argument.type);
            ST variableTemplate = templates.getInstanceOf("DeclarationAndAssignment");
            variableTemplate.add("memory_register", createdVariable.nameInCode);
            variableTemplate.add("value_register", argument.nameInCode);
            variableTemplate.add("has_value", 1);
            variableTemplate.add("type", createdVariable.type.getNameInLLVM());
            variables.peek().put(ctx.VARIABLE(i).getText(), createdVariable);

            // add it to the code of the function
            functionTemplate.add("code", variableTemplate.render());

            functionInfo.arguments.add(argument);
        }

        // put into the registry for functions
        functions.put(ctx.name.getText(), functionInfo);

        // and set as the current function
        currentFunction = functionInfo;

        functionTemplate.add("name", functionInfo.nameInCode);
        functionTemplate.add("return_type", functionInfo.returnType.getNameInLLVM());

        // make a nice looking list of arguments
        functionTemplate.add("arguments", functionInfo.arguments.stream()
                .map(variableInfo -> variableInfo.type.getNameInLLVM() + " " + variableInfo.nameInCode)
                .collect(Collectors.joining(", ")));

        // add code inside the function
        CodeFragment code = visit(ctx.block());
        functionTemplate.add("code", code);

        // pop the scope and unset the current function
        variables.pop();
        currentFunction = null;

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
        variables.pop();
        return new CodeFragment(template.render());
    }

    @Override
    public CodeFragment visitArrayElement(C_s_makcenomParser.ArrayElementContext ctx) {
        // check if name is defined
        String arrayName = ctx.array.getText();
        if (!isVariableNameUsed(arrayName)) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Neznáma premenná \"" + arrayName + "\" (inak, v Č treba pred použitím deklarovať)");
            return new CodeFragment();
        }

        // get the location of the variable, so stuff can be stored inside
        VariableInfo info = getVariableInfo(arrayName);
        assert info != null;

        // different approaches for static and dynamic arrays
        if (info.type.type.getFirst() == Type.Types.TABLE) {
            ST tableTemplate = templates.getInstanceOf("GetTableElement");
            tableTemplate.add("memory_register", info.nameInCode);
            tableTemplate.add("type", info.type.getNameInLLVM());
            tableTemplate.add("base_type", info.type.getBaseTypeNameInLLVM());
            tableTemplate.add("label_id", generateNewLabel());

            // index
            if (ctx.index != null) {
                // simple variable
                CodeFragment indexCode = visitVariableFromMorePlaces(ctx.index.getText(), ctx.getStart().getLine());
                tableTemplate.add("calculate_index", indexCode);
                tableTemplate.add("index_registers", "i32 " + indexCode.resultRegisterName);
            } else {
                // expression, possibly multidimensional
                // visit all
                List<CodeFragment> indexCodes = ctx.num_expr().stream().map(this::visit).toList();

                tableTemplate.add("calculate_index", String.join("\r\n", indexCodes.stream().map(CodeFragment::toString).toList()));
                tableTemplate.add("index_registers", String.join(", ", indexCodes.stream().map(codeFragment -> "i32 " + codeFragment.resultRegisterName).toList()));
            }


            String uniqueName = generateUniqueRegisterName("");
            tableTemplate.add("return_register", uniqueName);

            return new CodeFragment(tableTemplate.render(), uniqueName);
        } else {
            return null;
        }
    }

    @Override
    public CodeFragment visitCharOfText(C_s_makcenomParser.CharOfTextContext ctx) {
        return null;
    }

    private CodeFragment visitVariableFromMorePlaces(String name, int line) {
        ST getFromVariableTemplate = templates.getInstanceOf("GetFromVariable");

        // check if name is defined
        String variableName = name;
        if (!isVariableNameUsed(variableName)) {
            errorCollector.add("Problém na riadku " + line
                    + ": Neznáma premenná \"" + variableName + "\", treba ju definovať");
            return new CodeFragment();
        }

        // get the location of the variable, so stuff can be stored inside
        VariableInfo info = getVariableInfo(variableName);
        assert info != null;
        getFromVariableTemplate.add("memory_register", info.nameInCode);

        getFromVariableTemplate.add("type", info.type.getNameInLLVM());
        String uniqueName = generateUniqueRegisterName("");
        getFromVariableTemplate.add("return_register", uniqueName);

        return new CodeFragment(getFromVariableTemplate.render(), uniqueName);
    }

    @Override
    public CodeFragment visitVariable(C_s_makcenomParser.VariableContext ctx) {
        return visitVariableFromMorePlaces(ctx.VARIABLE().getText(), ctx.getStart().getLine());
    }

    @Override
    public CodeFragment visitExpr(C_s_makcenomParser.ExprContext ctx) {
        CodeFragment codeFragment = null;

        if (ctx.num_expr() != null) {
            codeFragment = visit(ctx.num_expr());
        } else if (ctx.logic_expr() != null) {
            codeFragment = visit(ctx.logic_expr());
        } else if (ctx.CHARACTER() != null) {
            // all chars should be of the format "'c'", so we need the character on position 1
            int character = ctx.CHARACTER().getText().charAt(1);

            // check whether the character fits in i8 and whether it isn't something more complicated (like a Chinese character)
            if (character > 0xFF || ctx.CHARACTER().getText().length() != 3) {
                errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                        + ": \"" + ctx.CHARACTER().getText() + "\" tu nemožno použiť, treba len písmenko bez diakritiky :/");
                return new CodeFragment();
            }

            // once again, we convert the character to a number and pass it directly as an "output register"
            codeFragment = new CodeFragment("", Integer.toString(character));
        } else if (ctx.funcion_expr() != null) {
            codeFragment = visit(ctx.funcion_expr());
        } // TODO

        return codeFragment;
    }

    @Override
    public CodeFragment visitExprParen(C_s_makcenomParser.ExprParenContext ctx) {
        return visit(ctx.num_expr());
    }

    @Override
    public CodeFragment visitIdentifier(C_s_makcenomParser.IdentifierContext ctx) {
        // TODO
        return visit(ctx.id());
    }

    @Override
    public CodeFragment visitNegative(C_s_makcenomParser.NegativeContext ctx) {
        ST negativeTemplate = templates.getInstanceOf("Negative");

        CodeFragment inner = visit(ctx.num_expr());
        negativeTemplate.add("compute_value", inner);
        negativeTemplate.add("value_register", inner.resultRegisterName);
        String uniqueName = generateUniqueRegisterName("");
        negativeTemplate.add("return_register", uniqueName);

        return new CodeFragment(negativeTemplate.render(), uniqueName);
    }

    @Override
    public CodeFragment visitNumber(C_s_makcenomParser.NumberContext ctx) {
        // hilarious hack: we place the values as the "register", because it works with our templates :P
        return new CodeFragment("", ctx.NUMBER().getText());
    }

    @Override
    public CodeFragment visitBinaryOperation(C_s_makcenomParser.BinaryOperationContext ctx) {
        ST BinOpTemplate = templates.getInstanceOf("BinOp");
        BinOpTemplate.add("type", "i32");

        // find out which operation we're doing
        String operator = switch(ctx.op.getType()) {
            case C_s_makcenomParser.MULTIPLICATION -> "mul";
            case C_s_makcenomParser.DIVISION -> "sdiv";
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

        CodeFragment left = visit(ctx.left);
        CodeFragment right = visit(ctx.right);

        BinOpTemplate.add("compute_left", left);
        BinOpTemplate.add("compute_right", right);
        BinOpTemplate.add("left_register", left.resultRegisterName);
        BinOpTemplate.add("right_register", right.resultRegisterName);

        String uniqueName = generateUniqueRegisterName("");
        BinOpTemplate.add("return_register", uniqueName);

        return new CodeFragment(BinOpTemplate.render(), uniqueName);
    }

    @Override
    public CodeFragment visitArraySize(C_s_makcenomParser.ArraySizeContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitLogicalValue(C_s_makcenomParser.LogicalValueContext ctx) {
        // we use the same trick of just putting the value as the "output register"
        return new CodeFragment("", ctx.val.getType() == C_s_makcenomParser.FALSE ? "0" : "1");
    }

    @Override
    public CodeFragment visitNegation(C_s_makcenomParser.NegationContext ctx) {
        ST negationTemplate = templates.getInstanceOf("Negation");

        CodeFragment inner = visit(ctx.logic_expr());
        negationTemplate.add("compute_value", inner);
        negationTemplate.add("value_register", inner.resultRegisterName);
        String uniqueName = generateUniqueRegisterName("");
        negationTemplate.add("return_register", uniqueName);

        return new CodeFragment(negationTemplate.render(), uniqueName);
    }

    @Override
    public CodeFragment visitBinaryRelationOperation(C_s_makcenomParser.BinaryRelationOperationContext ctx) {
        ST BinOpTemplate = templates.getInstanceOf("RelationBinOp");
        BinOpTemplate.add("type", "i32");

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

        CodeFragment left = visit(ctx.left);
        CodeFragment right = visit(ctx.right);

        BinOpTemplate.add("compute_left", left);
        BinOpTemplate.add("compute_right", right);
        BinOpTemplate.add("left_register", left.resultRegisterName);
        BinOpTemplate.add("right_register", right.resultRegisterName);

        String uniqueName = generateUniqueRegisterName("");
        BinOpTemplate.add("return_register", uniqueName);

        return new CodeFragment(BinOpTemplate.render(), uniqueName);
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

        CodeFragment left = visit(ctx.left);
        CodeFragment right = visit(ctx.right);

        BinOpTemplate.add("compute_left", left);
        BinOpTemplate.add("compute_right", right);
        BinOpTemplate.add("left_register", left.resultRegisterName);
        BinOpTemplate.add("right_register", right.resultRegisterName);

        String uniqueName = generateUniqueRegisterName("");
        BinOpTemplate.add("return_register", uniqueName);

        return new CodeFragment(BinOpTemplate.render(), uniqueName);
    }

    @Override
    public CodeFragment visitLogicIdentifier(C_s_makcenomParser.LogicIdentifierContext ctx) {
        // TODO
        return visit(ctx.id());
    }

    @Override
    public CodeFragment visitLogicParen(C_s_makcenomParser.LogicParenContext ctx) {
        return visit(ctx.logic_expr());
    }

    @Override
    public CodeFragment visitArray_expr(C_s_makcenomParser.Array_exprContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitType(C_s_makcenomParser.TypeContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitInput_type(C_s_makcenomParser.Input_typeContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitOf_type(C_s_makcenomParser.Of_typeContext ctx) {
        return null;
    }
}

package eo.cxivo;

import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.stringtemplate.v4.*;
import java.util.HashMap;
import java.util.Stack;


public class LanguageVisitor extends C_s_makcenomBaseVisitor<CodeFragment> {
    STGroup templates = new STGroupFile("helper-libs/basics.stg");

    // new HashSet get pushed with every new scope
    private final Stack<HashMap<String, VariableInfo>> variables = new Stack<>();
    private final ErrorCollector errorCollector;
    private int registerIndex = 0;
    private int labelIndex = 0;

    private String generateUniqueRegisterName(String originalName) {
        if (originalName.isEmpty()) {
            return "%reg_" + registerIndex++;
        } else {
            return "%" + originalName.toLowerCase() + "_" + registerIndex++;
        }
    }

    private String generateNewLabel() {
        return Integer.toString(labelIndex++);
    }


    private boolean isVariableNameUsed(String name) {
        return variables.stream().anyMatch(map -> map.containsKey(name));
    }

    private VariableInfo getVariableInfo(String name) {
        for (HashMap<String, VariableInfo> map: variables) {
            if (map.containsKey(name)) {
                return map.get(name);
            }
        }
        return null;
    }

    public LanguageVisitor(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    @Override
    public CodeFragment visitInitial(C_s_makcenomParser.InitialContext ctx) {
        // initialize everything I can
        variables.push(new HashMap<>());

        // prazdny template pre init
        ST template = templates.getInstanceOf("base");

        // potrebujeme doplnit hodnotu pre atribut "code"
        int n = ctx.statement().size();
        for (int i = 0; i < n; i++) {
            // rekurzivne vypocitame CodeFragment pre kazdy "stat"
            CodeFragment statCodeFragment = visit(ctx.statement(i));

            // kod z fragmentu prilepime do atributu "code"
            template.add("code", statCodeFragment + "\r\n");
        }

        // vysledny CodeFragment obsahuje pospajane kody z jednotlivych "stat"
        // vysledny register sa nevyplna, nie je potrebny
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

        VariableInfo.Type type = null;
        if (ctx.var_type.INT() != null) {
            // i32
            type = VariableInfo.Type.INT;
            declarationTemplate.add("type", "i32");
        } else if (ctx.var_type.BOOL() != null) {
            // i8
            type = VariableInfo.Type.BOOL;
            declarationTemplate.add("type", "i8");
        } else if (ctx.var_type.CHAR() != null) {
            // i8
            type = VariableInfo.Type.CHAR;
            declarationTemplate.add("type", "i8");
        }

        // allocate space for it
        String registerName = generateUniqueRegisterName(variableName);
        variables.peek().put(variableName, new VariableInfo(registerName, type));

        declarationTemplate.add("memory_register", registerName);

        // calculate initial value of the variable
        CodeFragment calculation = new CodeFragment();
        if (ctx.expr() != null) {
            calculation = visit(ctx.expr());
            declarationTemplate.add("has_value", 1);
            declarationTemplate.add("compute_value", calculation.toString());
            declarationTemplate.add("value_register", calculation.resultRegisterName);
        }

        return new CodeFragment(declarationTemplate.render());
    }

    @Override
    public CodeFragment visitConditional(C_s_makcenomParser.ConditionalContext ctx) {
        return null;
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
        } // TODO

        assert outputTemplate != null;
        return new CodeFragment(outputTemplate.render());
    }

    @Override
    public CodeFragment visitPrintNewLine(C_s_makcenomParser.PrintNewLineContext ctx) {
        return null;
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
        return null;
    }

    @Override
    public CodeFragment visitProcedureCall(C_s_makcenomParser.ProcedureCallContext ctx) {
        return null;
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

        // do different things based on the type
        String llvm_type = switch (info.type) {
            case BOOL, CHAR -> "i8";
            case INT -> "i32";
        };

        // pointer if it's an array
        if (info.arrayDimension > 0) {
            llvm_type = "ptr";
            // TODO array
        }
        variableAssignmentTemplate.add("type", llvm_type);


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
        return null;
    }

    @Override
    public CodeFragment visitCharOfTextAssignment(C_s_makcenomParser.CharOfTextAssignmentContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitFunctionDefinition(C_s_makcenomParser.FunctionDefinitionContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitBlock(C_s_makcenomParser.BlockContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitArrayElement(C_s_makcenomParser.ArrayElementContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitCharOfText(C_s_makcenomParser.CharOfTextContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitVariable(C_s_makcenomParser.VariableContext ctx) {
        ST getFromVariableTemplate = templates.getInstanceOf("GetFromVariable");

        // check if name is defined
        String variableName = ctx.VARIABLE().getText();
        if (!isVariableNameUsed(variableName)) {
            errorCollector.add("Problém na riadku " + ctx.getStart().getLine()
                    + ": Neznáma premenná \"" + variableName + "\", treba ju definovať");
            return new CodeFragment();
        }

        // get the location of the variable, so stuff can be stored inside
        VariableInfo info = getVariableInfo(variableName);
        assert info != null;
        getFromVariableTemplate.add("memory_register", info.nameInCode);

        // do different things based on the type
        String llvm_type = switch (info.type) {
            case BOOL, CHAR -> "i8";
            case INT -> "i32";
        };

        // pointer if it's an array
        if (info.arrayDimension > 0) {
            llvm_type = "ptr";
            // TODO array
        }

        getFromVariableTemplate.add("type", llvm_type);
        String uniqueName = generateUniqueRegisterName("");
        getFromVariableTemplate.add("return_register", uniqueName);

        return new CodeFragment(getFromVariableTemplate.render(), uniqueName);
    }

    @Override
    public CodeFragment visitExpr(C_s_makcenomParser.ExprContext ctx) {
        CodeFragment codeFragment = null;

        if (ctx.num_expr() != null) {
            codeFragment = visit(ctx.num_expr());
        } else if (ctx.logic_expr() != null) {
            codeFragment = visit(ctx.logic_expr());
        } // TODO

        return codeFragment;
    }

    @Override
    public CodeFragment visitExprParen(C_s_makcenomParser.ExprParenContext ctx) {
        return visit(ctx.num_expr());
    }

    @Override
    public CodeFragment visitIdentifier(C_s_makcenomParser.IdentifierContext ctx) {
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
        ST numBinOpTemplate = templates.getInstanceOf("NumBinOp");

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

        numBinOpTemplate.add("instruction", operator);

        CodeFragment left = visit(ctx.left);
        CodeFragment right = visit(ctx.right);

        numBinOpTemplate.add("compute_left", left);
        numBinOpTemplate.add("compute_right", right);
        numBinOpTemplate.add("left_register", left.resultRegisterName);
        numBinOpTemplate.add("right_register", right.resultRegisterName);

        String uniqueName = generateUniqueRegisterName("");
        numBinOpTemplate.add("return_register", uniqueName);

        return new CodeFragment(numBinOpTemplate.render(), uniqueName);
    }

    @Override
    public CodeFragment visitArraySize(C_s_makcenomParser.ArraySizeContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitLogicalValue(C_s_makcenomParser.LogicalValueContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitNegation(C_s_makcenomParser.NegationContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitBinaryRelationOperation(C_s_makcenomParser.BinaryRelationOperationContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitBinaryLogicOperation(C_s_makcenomParser.BinaryLogicOperationContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitLogicIdentifier(C_s_makcenomParser.LogicIdentifierContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitLogicParen(C_s_makcenomParser.LogicParenContext ctx) {
        return null;
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

    @Override
    public CodeFragment visitChildren(RuleNode ruleNode) {
        return null;
    }

    @Override
    public CodeFragment visitTerminal(TerminalNode terminalNode) {
        return null;
    }

    @Override
    public CodeFragment visitErrorNode(ErrorNode errorNode) {
        return null;
    }
}

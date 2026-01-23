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
    private Stack<HashMap<String, VariableInfo>> variables = new Stack<>();

    private int registerIndex = 0;

    private int labelIndex = 0;

    private String generateUniqueRegisterName(String originalName) {
        if (originalName.isEmpty()) {
            return "%reg_" + registerIndex++;
        } else {
            return "%" + originalName.toLowerCase() + "_" + registerIndex++;
        }
    }

    /* generovanie unikatnych labelov pre bloky
       if - iftrue0, iffalse0, fi0, iftrue1, iffalse1, fi1,...
       for - cycle0, iter0, endcycle0, cycle1, iter1, endcycle1, ...
     */
    private String generateNewLabel() {
        return Integer.toString(labelIndex++);
    }


    private boolean isVariableNameUsed(String name) {
        boolean used = false;
        for (HashMap<String, VariableInfo> map : variables) {
            used = used || map.containsKey(name);
        }
        return used;
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
            //throw new Exception("Názov \"" + variableName + "\" je už použitý ");
        }

        if (ctx.var_type.INT() != null) {
            // i32
            declarationTemplate.add("type", "i32");
        } else if (ctx.var_type.BOOL() != null || ctx.var_type.CHAR() != null) {
            // i8
            declarationTemplate.add("type", "i8");
        }

        // allocate space for it
        String registerName = generateUniqueRegisterName(variableName);
        variables.peek().put(variableName, new VariableInfo(registerName));

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
        return null;
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
    public CodeFragment visitAssignment(C_s_makcenomParser.AssignmentContext ctx) {

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
        return null;
    }

    @Override
    public CodeFragment visitExpr(C_s_makcenomParser.ExprContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitExprParen(C_s_makcenomParser.ExprParenContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitIdentifier(C_s_makcenomParser.IdentifierContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitNegative(C_s_makcenomParser.NegativeContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitNumber(C_s_makcenomParser.NumberContext ctx) {
        return null;
    }

    @Override
    public CodeFragment visitBinaryOperation(C_s_makcenomParser.BinaryOperationContext ctx) {
        return null;
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

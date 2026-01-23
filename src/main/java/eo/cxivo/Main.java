package eo.cxivo;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.*;

public class Main {

    // I won't lie, I can't really do this any differently
    public static void main(String[] args) throws Exception {
        CharStream in = CharStreams.fromFileName("test-data/krátkyTest.č");
        PrintStream out = new PrintStream(new FileOutputStream("program.ll"));  //System.out;

        eo.cxivo.C_s_makcenomLexer lexer = new eo.cxivo.C_s_makcenomLexer(in);

        // print all tokens
        //for (Token t : lexer.getAllTokens()) { System.out.println(t); }

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        eo.cxivo.C_s_makcenomParser parser = new eo.cxivo.C_s_makcenomParser(tokens);

        ParseTree tree = parser.initial();

        // parse code
        ErrorCollector errorCollector = new ErrorCollector();
        LanguageVisitor visitor = new LanguageVisitor(errorCollector);

        // the grand visit
        CodeFragment codeFragment = visitor.visit(tree);

        // how did we do?
        if (errorCollector.getErrorCount() == 0) {
            out.print(codeFragment.toString());
        } else {
            System.err.println(errorCollector);
        }
    }
}
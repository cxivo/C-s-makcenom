package eo.cxivo;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.*;
import java.util.Collection;
import java.util.Collections;

public class Main {

    // I won't lie, I can't really do this any differently
    public static void main(String[] args) throws Exception {
        CharStream in = CharStreams.fromFileName("test-data/krátkyTest.č");
        PrintStream out = new PrintStream(new FileOutputStream("output/program.ll"));  //System.out;

        // convert tabs and spaces to indents
        String program = in.toString();
        String[] lines = program.split("\n");

        int previousIndent = 0;
        int errorCount = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].strip().startsWith("(") || lines[i].isBlank()) {
                // is a comment or empty line; ignore
                continue;
            }

            if (lines[i].contains("{") || lines[i].contains("}")) {
                System.err.println("Problém na riadku " + i + ": Používajte tabulátory, nie kučeravé zátvorky!");
                errorCount++;
            }

            // count leading spaces
            int spaces = 0;
            for (char c: lines[i].toCharArray()) {
                if (c == ' ') {
                    spaces++;
                } else if (c == '\t') {
                    spaces += 4;
                } else {
                    break;
                }
            }

            if (spaces % 4 != 0) {
                System.err.println("Problém na riadku " + i + ": Na odsadenie používajte len tabulátory a medzery, "
                        + "a nezabudnite že jeden tabulátor sú 4 medzery!! Lebo som povedala.");
                errorCount++;
            }

            int currentIndent = spaces / 4;
            if (currentIndent > previousIndent) {
                lines[i] = String.join("", Collections.nCopies(currentIndent - previousIndent, "{\n")) + lines[i];
            } else if (currentIndent < previousIndent) {
                lines[i] = String.join("", Collections.nCopies(previousIndent - currentIndent, "}\n")) + lines[i];
            }

            previousIndent = currentIndent;
        }

        if (errorCount > 0) {
            return;
        }

        String edited = "\n" + String.join("\n", lines) + String.join("", Collections.nCopies(previousIndent, "}\n")) + "\n";
        // this is the one the grammar works with
        //System.out.println(edited);
        CharStream inEdited = CharStreams.fromString(edited);

        eo.cxivo.C_s_makcenomLexer lexer = new eo.cxivo.C_s_makcenomLexer(inEdited);

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
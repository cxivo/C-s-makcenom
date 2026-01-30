package eo.cxivo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Type {
    public static Type BOOL = new Type(Primitive.BOOL);
    public static Type CHAR = new Type(Primitive.CHAR);
    public static Type INT = new Type(Primitive.INT);
    public static Type VOID = new Type(Primitive.VOID);

    public enum Primitive {
        BOOL,
        CHAR,
        INT,
        VOID    // an impostor, but it makes things sooo much easier
    }

    public Primitive primitive;
    public int listDimensions = 0;
    public List<Integer> tableLengths = new ArrayList<>();

    public Type(Primitive type) {
        this.primitive = type;
    }

    public Type(C_s_makcenomParser.TypeContext context, ErrorCollector errorCollector) {
        if (context == null) {
            primitive = Primitive.VOID;
        } else if (context.CHAR() != null) {
            primitive = Primitive.CHAR;
        } else if (context.INT() != null) {
            primitive = Primitive.INT;
        } else if (context.BOOL() != null) {
            primitive = Primitive.BOOL;
        } else if (context.STRING() != null) {
            primitive =  Primitive.CHAR;
            listDimensions = 1;
        } else if (context.TABLE() != null) {
            this.primitive = (new Type(context.of_primitives())).primitive;

            // get the table sizes
            this.tableLengths = context.NUMBER().stream().map(terminalNode -> Integer.parseInt(terminalNode.getText())).toList();

            // all sizes must be nonzero positive
            if (tableLengths.stream().anyMatch(integer -> integer <= 0)) {
                errorCollector.add("Problém na riadku " + context.getStart().getLine()
                        + ": Tabuľka musí mať kladné celé čísla ako veľkosti");
            }
        } else {
            // List
            this.listDimensions = 1 + context.OF_LISTS().size();
            this.primitive = (new Type(context.of_primitives())).primitive;
        }
    }

    public Type(C_s_makcenomParser.Of_primitivesContext context) {
        if (context.OF_CHARS() != null) {
            primitive = Primitive.CHAR;
        } else if (context.OF_INTS() != null) {
            primitive = Primitive.INT;
        } else if (context.OF_BOOLS() != null) {
            primitive = Primitive.BOOL;
        } else {
            primitive = Primitive.VOID;
        }
    }

    public static Type copyOf(Type t) {
        Type type = new Type(t.primitive);
        type.listDimensions = t.listDimensions;
        type.tableLengths = new ArrayList<>(t.tableLengths);
        return type;
    }

    public String getNameInLLVM() {
        if (listDimensions > 0) {
            // list
            return "ptr";
        } else if (!tableLengths.isEmpty()) {
            // table
            return String.join(
                    "",
                    tableLengths.stream().map(i -> "[" + i + " x ").toList())
                    + getBaseTypeNameInLLVM()
                    + String.join("", Collections.nCopies(tableLengths.size(), "]"));
        } else {
            // primitive
            return getBaseTypeNameInLLVM();
        }
    }

    public String getBaseTypeNameInLLVM() {
        return switch (primitive) {
            case BOOL -> "i1";
            case CHAR -> "i8";
            case INT -> "i32";
            case VOID -> "void";
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Type other) {
            return this.primitive.equals(other.primitive) && this.tableLengths.equals(other.tableLengths) && this.listDimensions == other.listDimensions;
        } else {
            return false;
        }
    }
}

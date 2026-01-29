package eo.cxivo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Type {
    public enum Types {
        BOOL,
        CHAR,
        INT,
        LIST,
        TABLE,
        VOID
    }

    public List<Types> type;
    public List<Integer> table_size = new ArrayList<>();

    public Type(List<Types> type) {
        this.type = type;
    }

    public Type(C_s_makcenomParser.TypeContext context, ErrorCollector errorCollector) {
        if (context == null) {
            type = List.of(Types.VOID);
        } else if (context.CHAR() != null) {
            type = List.of(Types.CHAR);
        } else if (context.INT() != null) {
            type = List.of(Types.INT);
        } else if (context.BOOL() != null) {
            type = List.of(Types.BOOL);
        } else if (context.STRING() != null) {
            type = List.of(Types.LIST, Types.CHAR);
        } else if (context.TABLE() != null) {
            Type innerType = new Type(context.of_type());
            type = new ArrayList<>();
            type.add(Types.TABLE);

            // get the table sizes
            table_size = context.NUMBER().stream().map(terminalNode -> Integer.parseInt(terminalNode.getText())).toList();

            // all sizes must be nonzero positive
            if (table_size.stream().anyMatch(integer -> integer <= 0)) {
                errorCollector.add("Problém na riadku " + context.getStart().getLine()
                        + ": Tabuľka musí mať kladné celé čísla ako veľkosti");
            }

            type.addAll(innerType.type);
        } else {
            Type innerType = new Type(context.of_type());
            type = new ArrayList<>();
            type.add(Types.LIST);
            type.addAll(innerType.type);
        }
    }

    public Type(C_s_makcenomParser.Of_typeContext context) {
        if (context.OF_CHARS() != null) {
            type = List.of(Types.CHAR);
        } else if (context.OF_INTS() != null) {
            type = List.of(Types.INT);
        } else if (context.OF_BOOLS() != null) {
            type = List.of(Types.BOOL);
        } else if (context.OF_STRINGS() != null) {
            type = List.of(Types.LIST, Types.CHAR);
        } else {
            Type innerType = new Type(context.of_type());
            type = List.of(Types.LIST);
            type.addAll(innerType.type);
        }
    }

    public String getNameInLLVM() {
        return switch (type.getFirst()) {
            case BOOL -> "i1";
            case CHAR -> "i8";
            case INT -> "i32";
            case LIST -> "ptr";
            case TABLE -> String.join(
                    "",
                    table_size.stream().map(i -> "[" + Integer.toString(i) + " x ").toList())
                    + getBaseTypeNameInLLVM()
                    + String.join("", Collections.nCopies(table_size.size(), "]"));
            case VOID -> "void";
        };
    }

    public String getBaseTypeNameInLLVM() {
        return switch (type.getLast()) {
            case BOOL -> "i1";
            case CHAR -> "i8";
            case INT -> "i32";
            case LIST -> "ptr";
            default -> "";      // will not happen
        };
    }
}

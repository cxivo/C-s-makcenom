package eo.cxivo;

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

    public Type(Types type) {
        this.type = List.of(type);
    }

    public Type(List<Types> type) {
        this.type = type;
    }

    public Type(C_s_makcenomParser.TypeContext context) {
        if (context.CHAR() != null) {
            type = List.of(Types.CHAR);
        } else if (context.INT() != null) {
            type = List.of(Types.INT);
        } else if (context.BOOL() != null) {
            type = List.of(Types.BOOL);
        } else if (context.STRING() != null) {
            type = List.of(Types.LIST, Types.CHAR);
        } else if (context.TABLE() != null) {
            Type innerType = new Type(context.of_type());
            type = List.of(Types.TABLE);
            type.addAll(innerType.type);
        } else {
            Type innerType = new Type(context.of_type());
            type = List.of(Types.LIST);
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
            case LIST, TABLE -> "ptr";
            case VOID -> "void";
        };
    }
}

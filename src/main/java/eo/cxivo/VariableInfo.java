package eo.cxivo;

public class VariableInfo {
    public enum Type {
        BOOL,
        CHAR,
        INT
    }

    public String nameInCode;
    public Type type;
    public int arrayDimension = 0;  // 0 for regular variables, otherwise is the dimension of the array

    public VariableInfo(String nameInCode, Type type) {
        this.nameInCode = nameInCode;
        this.type = type;
    }

    public VariableInfo(String nameInCode, Type type, int dimension) {
        this(nameInCode, type);
        this.arrayDimension = dimension;
    }
}

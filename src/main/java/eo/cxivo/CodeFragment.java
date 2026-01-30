package eo.cxivo;

public class CodeFragment {
    public String code;
    public String resultRegisterName;
    public Type type;   // only used when there is a result register

    public CodeFragment() {
        this.code = "";
        this.resultRegisterName = "";
        this.type = Type.VOID;   // a code fragment doesn't return anything by default
    }

    public CodeFragment(String code) {
        this();
        this.code = code;
    }

    public CodeFragment(String code, String resultRegisterName, Type type) {
        this(code);
        this.resultRegisterName = resultRegisterName;
        this.type = type;
    }

    @Override
    public String toString() {
        return code;
    }
}

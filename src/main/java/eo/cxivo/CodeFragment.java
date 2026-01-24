package eo.cxivo;

public class CodeFragment {
    public String code;
    public String resultRegisterName;

    public CodeFragment() {
        this.code = "";
        this.resultRegisterName = "";
    }

    public CodeFragment(String code) {
        this();
        this.code = code;
    }

    public CodeFragment(String code, String resultRegisterName) {
        this(code);
        this.resultRegisterName = resultRegisterName;
    }

    @Override
    public String toString() {
        return code;
    }
}

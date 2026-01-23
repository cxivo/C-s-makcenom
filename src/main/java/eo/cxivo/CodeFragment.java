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

    @Override
    public String toString() {
        return code;
    }
}

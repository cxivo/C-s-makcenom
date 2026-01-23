package eo.cxivo;

import java.util.ArrayList;
import java.util.List;

public class ErrorCollector {
    private final List<String> messages = new ArrayList<>();

    public void add(String message) {
        this.messages.add(message);
    }

    @Override
    public String toString() {
        return String.join("\r\n", messages);
    }

    public int getErrorCount() {
        return messages.size();
    }
}

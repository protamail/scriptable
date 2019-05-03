package com.bkmks.template;

public class SoyStringBuilder {

    StringBuilder sb = new StringBuilder();

    public void append(Object... vv) {
        for (Object v: vv)
            sb.append(v.toString());
    }

    public String toString() {
        return sb.toString();
    }

}


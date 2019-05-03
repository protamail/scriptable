package com.bkmks.template;

import java.io.Writer;
import org.mozilla.javascript.Function;

import java.io.IOException;

public class SoyStringOutput {

    Writer httpWriter;
    StringBuilder sb = new StringBuilder();

    public SoyStringOutput(Writer v) {
        httpWriter = v;
    }

    public void append(Object... vv) throws IOException {
        if (vv.length > 1) {
            for (Object v: vv) {
                if (v instanceof Function) {
                    writeOut();
                    com.bkmks.RhinoHttpRequest.callJsFunction((Function)v, this);
                }
                else if (v instanceof CharSequence)
                    sb.append((CharSequence)v);
                else
                    sb.append(v.toString());
            }
            writeOut();
        }
        else if (vv.length == 1) {
            if (vv[0] instanceof Function)
                com.bkmks.RhinoHttpRequest.callJsFunction((Function)vv[0], this);
            else {
                httpWriter.write(vv[0].toString());
                httpWriter.flush();
            }
        }
    }

    public void writeOut() throws IOException {
        if (sb.length() > 0) {
            httpWriter.write(sb.toString());
            httpWriter.flush();
            sb.setLength(0);
        }
    }

    public String toString() {
        return "";
    }

}


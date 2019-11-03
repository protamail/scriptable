package org.scriptable;

import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.io.File;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import org.scriptable.HttpRequest;
import org.scriptable.ScriptableHttpRequest;
import org.scriptable.util.Files;
import org.scriptable.util.JavaProcess;
import org.mozilla.javascript.Scriptable;
import java.util.concurrent.Callable;
import org.mozilla.javascript.Function;

import java.io.IOException;

/**
 * Compilation task which invokes javascript based compilation procedure
 */
public class ScriptableCompileTask implements Callable {
    String jsModuleName;

    public void setJsModuleName(String v) {
        jsModuleName = "/" + v;
    }

    public String getJsModuleName() {
        return jsModuleName;
    }

    String jsFunctionName;

    public void setJsFunctionName(String v) {
        jsFunctionName = v;
    }

    public String getJsFunctionName() {
        return jsFunctionName;
    }

    String basePropertyName;

    public void setBasePropertyName(String v) {
        basePropertyName = v;
    }

    public String getBasePropertyName() {
        return basePropertyName;
    }

    String documentRoot = null;

    public String getDocumentRoot() {
        return HttpRequest.getDocumentRoot();
    }

    public void setDocumentRoot(String v) throws IOException {
        documentRoot = v;
        HttpRequest.setDocumentRoot(v);
    }

    String releasing = null;

    public String getReleasing() {
        return releasing;
    }

    public void setReleasing(String v) throws IOException {
        releasing = v;
    }

    @Override
    public Object call() throws Exception {
        Map<String, Object> opt = new ScriptableMap(false);

        if (releasing != null && releasing.equals("release"))
            opt.put("releasing", true);

        opt.put("compilationMode", true);

        return ScriptableHttpRequest.callJsFunction(jsModuleName, jsFunctionName, basePropertyName, opt);
    }

    public void execute() {

        try {

            // do validation before initializing JS env
            if (jsModuleName == null || jsFunctionName == null || basePropertyName == null ||
                    documentRoot == null)
                throw new RuntimeException("jsModuleName, jsFunctionName, documentRoot, and basePropertyName must be specified");

            ScriptableHttpRequest.setCompilationMode(true);
            ScriptableHttpRequest.runInJsEnv(null, this);
        }
        catch (Exception e) {
            HttpRequest.logError(e.getMessage());
//            e.printStackTrace(System.err);

            try {
                // let ant know there was an error
                throw (RuntimeException) Class.forName("org.apache.tools.ant.BuildException")
                    .getConstructor(String.class)
                    .newInstance("Compile failed; see the compiler error output for details.");
            }
            catch (Exception e1) {
                if (e1 instanceof RuntimeException)
                    throw (RuntimeException) e1;
            }
        }
    }
}


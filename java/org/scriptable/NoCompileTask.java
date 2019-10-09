package org.scriptable;

import java.util.List;
import java.io.IOException;

/**
 * This class is used to get the pre-compiled source files in production environment
 */
public final class NoCompileTask extends CompileTask
{
    @Override protected void compileFiles(List<String[]> files)
        throws IOException, InterruptedException, CompileTaskException {
        throw new RuntimeException("compileFiles: Not implemented.");
    }
}


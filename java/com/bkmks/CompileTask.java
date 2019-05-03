package com.bkmks;

import java.util.List;
import java.util.LinkedList;
import java.io.File;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import com.bkmks.HttpRequest;
import com.bkmks.RhinoHttpRequest;
import com.bkmks.util.Files;
import com.bkmks.util.JavaProcess;

import java.io.IOException;

/**
 * This a base class for compilation tasks usable both standalone as well as an ant task
 */
abstract public class CompileTask implements Serializable
{
    /**
     * Validate any prerequisites before compilation
     */
    protected void validateBeforeCompile() {
        if (destDir == null && output == null)
            throw new RuntimeException("either destdir or output attribute must be set");
        if (destDir != null && output != null)
            throw new RuntimeException("destdir and output attributes are mutually exclusive");
        if ((src == null || srcext == null) && configPrefix == null)
            throw new RuntimeException("Either src and srcext, or configPrefix must be set");
    }

    /**
     * Indicate whether destination file needs to be refreshed (recompiled)
     */
    protected boolean destNeedsRefresh(File srcFile, File dstFile) throws IOException {
        return dstFile.lastModified() < srcFile.lastModified();
    }

    String srcext;
    public void setSrcext(String v) {
        srcext = v;
    }

    public String getSrcext() {
        return srcext;
    }

    String configPrefix;
    public void setConfigPrefix(String v) {
        configPrefix = v;
    }

    public String getConfigPrefix() {
        return configPrefix;
    }

    /**
     * Compile files
     * @param files A list of source and destination file path pairs to compile
     */
    abstract protected void compileFiles(List<String[]> files)
        throws IOException, InterruptedException, CompileTaskException;

    // either destDir or output (file name) should be specified, but not both
    protected String destDir, output, src, documentRoot;
    protected boolean fork = false;

    /**
     * Set destination base directory.
     */
    public void setDestdir(String v) throws IOException {
        destDir = Files.normalizePath(v);
    }

    /**
     * Set output file path
     */
    public void setOutput(String v) {
        output = v;
    }

    /**
     * Set source base directory.
     */
    public void setSrc(String v) throws IOException {
        src = Files.normalizePath(v);
    }

    /**
     * Set whether to compile in a new process
     */
    public void setFork(boolean v) {
        fork = v;
    }

    /**
     * Used by ant task
     */
    public void execute() {
        try {
            compile();
        }
        catch (Exception e) {
            HttpRequest.logError(e.getMessage());
            try {
                // let the ant know build failed
                throw (RuntimeException) Class.forName("org.apache.tools.ant.BuildException").
                    getConstructor(String.class).
                    newInstance("Compile failed; see the compiler error output for details.");
            }
            catch (Exception e1) {
                if (e1 instanceof RuntimeException)
                    throw (RuntimeException) e1;
            }
        }
    }

    /**
     * Find all of out-of-date files, producing a list of source and destination path pairs
     * @param result Should be passed as null by the caller
     */
    protected List<String[]> listFilesToCompile(File curSrc, String relDest, List<String[]> result)
        throws IOException {
        validateBeforeCompile();
        if (result == null)
            result = new LinkedList<String[]>();

        File[] files = curSrc.listFiles(); // could be null if directory not readable
        if (files != null) {
            for (File f: files) {
                String file = f.getName();
                if (!f.isFile()) {
                    if (!file.startsWith("."))
                        result = listFilesToCompile(f, relDest + file + "/", result);
                }
                else if (file.endsWith(getSrcext())) {
                    File dstFile = new File(output != null ? output :
                            destDir + relDest + file + ".obj");
                    if (destNeedsRefresh(f, dstFile))
                        result.add(new String[] { f.getPath(), dstFile.getPath() });
                }
            }
        }

        return result;
    }

    /**
     * Compile all out-of-date files
     */
    public void compile() throws IOException, InterruptedException, CompileTaskException {
        // will recreate this in a forked process
        documentRoot = HttpRequest.getDocumentRoot();

        List<String[]> files = listFilesToCompile(new File(src), "/", null);

        if (files.size() > 0) {
            HttpRequest.logInfo("Compiling " + files.size() + " file(s).");
            synchronized(getClass()) {
                if (!fork) {
                    compileFiles(files);
                }
                else {
                    Process process = JavaProcess.startPiped(CompileTask.class);
                    ObjectOutputStream object = new ObjectOutputStream(process.getOutputStream());
                    object.writeObject(this);
                    object.close();
                    // read STDERR before waiting on PID to prevent process blocking on buffer overflow
                    // Note, all the logs go to STDERR
                    String stderr = JavaProcess.getStderrAsString(process);
                    process.waitFor();
                    if (process.exitValue() != 0) {
                        HttpRequest.logError("Compiler process exited with status: " + process.exitValue() +
                                "\n" + stderr);
                        CompileTaskException e = null;
                        try {
                            ObjectInputStream input = new ObjectInputStream(process.getInputStream());

                            while (input.readLong() != 0L) {} // skip to the exception object marker
                            e = (CompileTaskException) input.readObject();
                        }
                        catch (Exception ie) {}

                        if (e != null)
                            throw e;
                    }
                    else
                        HttpRequest.logInfo("Process output:\n" + stderr);
                }
            }
        }
        else
            HttpRequest.logInfo("None of the files changed. Compilation skipped.");
    }

    /**
     * Get compiled file content, recompiling if needed
     * @file file path relative to the src/dest dir, with src extension
     */
    public String getCompiledText(String file)
        throws IOException, InterruptedException, CompileTaskException {
        File srcFile = new File(src + '/' + file);
        File dstFile = new File(output != null ? output :
                destDir + '/' + file + ".obj");

        if (destNeedsRefresh(srcFile, dstFile)) {
            compile();
        }

        return dstFile.exists() ? Files.getFileAsString(dstFile) : null;
    }

    /**
     * Get compiled file content, recompiling if needed
     * @file file path relative to the src/dest dir, with dest extension
     */
    public String getCompiledTextSwp(String file)
        throws IOException, InterruptedException, CompileTaskException {
        return getCompiledText(file.substring(0, file.length()-4));
    }

    /**
     * Make file writable for everyone. Used to make compiled files overwritable by different processes
     */
    protected static void makeWritableByAll(File file) throws IOException {
        file.setWritable(true, false);
        file.setReadable(true, false);
    }

    /**
     * Create the destination path directories as needed, making them writable by everyone
     */
    protected static void createDestPathDirs(File file) throws IOException {
        File dir = file.getParentFile();
        if (dir == null || dir.exists())
            return;
        if (!dir.getParentFile().exists())
            createDestPathDirs(dir); // fix any missing parent dirs recursively
        dir.mkdir();
        makeWritableByAll(dir);
        dir.setExecutable(true, false);
    }

    public String getDocumentRoot() {
        return documentRoot;
    }

    /**
     * Used to run compilation task in a separate process
     * The serialized instance of the compilation task object is expected on STDIN
     */
    public static void main(String[] args) {
        try {
            ObjectInputStream input = new ObjectInputStream(System.in);
            CompileTask task = (CompileTask) input.readObject();
            HttpRequest.setDocumentRoot(task.getDocumentRoot());
            task.setFork(false);
            task.compile();
            System.exit(0);
        }
        catch (CompileTaskException e) {
            try {
                ObjectOutputStream output = new ObjectOutputStream(System.out);
                output.writeLong(0L); // marker used to locate beginning of an object in STDOUT
                output.writeObject(e);
                output.flush();
            }
            catch (IOException ee) {}
        }
        catch (Exception e) {
            HttpRequest.logError(HttpRequest.getStackTrace(e));
        }

        System.exit(2);
    }
}


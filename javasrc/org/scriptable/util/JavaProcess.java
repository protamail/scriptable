package org.scriptable.util;

/**
 * Execute java class in a separate process and wait for its completion
 */

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import org.scriptable.ScriptableHttpRequest;

import java.io.IOException;

public final class JavaProcess
{
    private JavaProcess() {}

    public static Process start(Class clazz, String... args)
        throws IOException, InterruptedException {
        // will avoid child blocking on overfilled STDIO, when no one reading
        return start(true, clazz, args);
    }

    public static Process startPiped(Class clazz, String... args)
        throws IOException, InterruptedException {
        // must read STDERR/STDOUT to prevent child blocking
        return start(false, clazz, args);
    }

    public static Process start(boolean inheritIO, Class clazz, String... args)
        throws IOException, InterruptedException {
        ArrayList<String> command = new ArrayList<String>();

        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + "/bin/java";
        String docRoot = ScriptableHttpRequest.getDocumentRoot();
        String classpath = System.getProperty("java.class.path") + ":" + docRoot + "WEB-INF/classes";
        classpath = addFilesToClasspath(docRoot + "../lib", classpath);
        classpath = addFilesToClasspath(docRoot + "../lib/build", classpath);

        command.addAll(Arrays.asList(javaBin, "-client", "-cp", classpath, clazz.getCanonicalName()));
        if (args != null)
            command.addAll(Arrays.asList(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        if (inheritIO)
            builder.inheritIO();

        Process process = builder.start();
        return process;
    }

    public static String getStdoutAsString(Process process) throws IOException {
        return Files.getStreamAsString(process.getInputStream());
    }

    public static String getStderrAsString(Process process) throws IOException {
        return Files.getStreamAsString(process.getErrorStream());
    }

    private static String addFilesToClasspath(String path, String classpath) throws IOException {
        StringBuilder cp = new StringBuilder(classpath);
        for (File file: new File(path).listFiles()) {
            if (file.isFile()) {
                cp.append(":");
                cp.append(file.getPath());
            }
        }
        return cp.toString();
    }
}


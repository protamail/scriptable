package org.scriptable.util;

import org.scriptable.HttpRequest;
import org.scriptable.ScriptableMap;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Properties;

import java.io.IOException;

final public class Files
{
    /**
     * Get File object corresponding to a file name specified relative to document root
     */
    public static File getFile(String file) {
        return new File(HttpRequest.getDocumentRoot() + file);
    }

    /**
     * Return file lastModified time
     * @file path name of a file relative to the document root
     */
    public static long getLastModified(String file) {
        return getFile(file).lastModified();
    }

    /**
     * Return most recently updated file lastModified time, or 0 if any one file is missing/unreadable
     * @files array of path names relative to the document root
     */
    public static long getLastModified(String[] files) {
        long lastModified = 0;
        for (String file: files) {
            long newLastModified = getFile(file).lastModified();
            if (newLastModified > lastModified)
                lastModified = newLastModified;
            else if (newLastModified == 0)
                return 0;
        }
        return lastModified;
    }

    /**
     * Return file lastModified time
     */
    public static long getLastModified(File file) throws IOException {
        return file.lastModified();
    }

    public static String getRealPath(String file) {
        return getFile(file).getPath();
    }

    /**
     * Return file contents as byte array
     * @file path name of a file relative to the document root
     */
    public static byte[] getFileAsBytes(String file) throws IOException {
        return getFileAsBytes(getFile(file));
    }

    /**
     * Return file contents as byte array
     */
    public static byte[] getFileAsBytes(File file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copyStream(new FileInputStream(file), output);
        return output.toByteArray();
    }

    /**
     * Return file contents as string
     * @file path name of a file relative to the document root
     */
    public static String getFileAsString(String file) throws IOException {
        return getFileAsString(getFile(file));
    }

    /**
     * Return file contents as string
     * @files array of path names relative to the document root
     */
    public static String getFilesAsString(String[] files) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String file: files)
            sb.append(getFileAsString(getFile(file)));
        return sb.toString();
    }

    /**
     * Return file contents as string
     */
    public static String getFileAsString(File file) throws IOException {
        StringWriter writer = new StringWriter(2048);
        copyStream(new FileReader(file), writer);
        return writer.toString();
    }

    /**
     * Return files content as string
     */
    public static String getFilesAsString(File[] files) throws IOException {
        StringWriter writer = new StringWriter(8192);
        for (File file: files)
            copyStream(new FileReader(file), writer);
        return writer.toString();
    }

    /**
     * Reads stream completely and returns it as byte array
     */
    public static byte[] getStreamAsBytes(InputStream is) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copyStream(is, output);
        return output.toByteArray();
    }

    /**
     * Reads stream completely and returns it as a string (interpreted using system default encoding)
     */
    public static String getStreamAsString(InputStream is) throws IOException {
        return new String(getStreamAsBytes(is));
    }

    public static String getReaderAsString(Reader is) throws IOException {
        StringWriter writer = new StringWriter(8192);
        copyStream(is, writer);
        return writer.toString();
    }

    /**
     * Writes string content to a file
     */
    public static void writeStringToFile(String content, File file, boolean append) throws IOException {
        FileWriter writer = new FileWriter(file, append);
        writer.write(content);
        writer.close();
    }

    public static void writeStringToFile(String content, String file) throws IOException {
        writeStringToFile(content, getFile(file), false);
    }

    public static void writeStringToFile(String content, File file) throws IOException {
        writeStringToFile(content, file, false);
    }

    /**
     * Writes byte array to a file
     */
    public static void writeBytesToFile(byte[] content, File file, boolean append) throws IOException {
        FileOutputStream fos = new FileOutputStream(file, append);
        copyStream(new ByteArrayInputStream(content), fos);
        fos.close();
    }

    public static void writeBytesToFile(byte[] content, String file) throws IOException {
        writeBytesToFile(content, getFile(file), false);
    }

    public static void writeBytesToFile(byte[] content, File file) throws IOException {
        writeBytesToFile(content, file, false);
    }

    /**
     * Copy data from source to sink, returning number of bytes copied
     * Note: Since copyStream uses buffering, source or sink doesn't need to be buffered
     */
    public static int copyStream(InputStream is, OutputStream os) throws IOException {
        byte buf[] = new byte[4096];
        int c, l = 0;
        while ((c = is.read(buf)) != -1) {
            os.write(buf, 0, c);
            l += c;
        }
        os.flush();
        return l;
    }

    /**
     * Copy available data from source to sink, returning number of bytes copied
     * This method should not block, waighting for input
     * Note: Since copyStream uses buffering, source or sink doesn't need to be buffered
     */
    public static int copyStreamNB(InputStream is, OutputStream os) throws IOException {
        byte buf[] = new byte[4096];
        int c, l = 0;
        while ((c = is.available()) > 0 && (c = is.read(buf, 0, c > 4096? 4096 : c)) > 0) {
            os.write(buf, 0, c);
            l += c;
        }
        os.flush();
        return l;
    }

    public static int copyStreamNB(InputStream is, Writer os) throws IOException {
        byte buf[] = new byte[4096];
        int c, l = 0;
        while ((c = is.available()) > 0 && (c = is.read(buf, 0, c > 4096? 4096 : c)) > 0) {
            os.write(new String(buf, 0, c, "UTF-8"));
            l += c;
        }
        os.flush();
        return l;
    }

    /**
     * Copy data from source to sink, returning number of bytes copied
     * Note: Since copyStream uses buffering, source or sink doesn't need to be buffered
     */
    public static int copyStream(Reader reader, Writer writer) throws IOException {
        char buf[] = new char[2048];
        int c, l = 0;
        while ((c = reader.read(buf)) != -1) {
            writer.write(buf, 0, c);
            l += c;
        }
        writer.flush();
        return l;
    }

    /**
     * Extract the file name portion of the file path
     */
    public static String getFileName(String filepath) {
        int lastSlash = filepath.lastIndexOf('/');
        return lastSlash == -1 ? filepath : filepath.substring(lastSlash+1);
    }

    /**
     * Extract the base name of the file path
     */
    public static String getBaseName(String filepath) {
        filepath = getFileName(filepath);
        int lastDot = filepath.lastIndexOf('.');
        return lastDot == -1 ? filepath : filepath.substring(0, lastDot);
    }

    /**
     * Extract file extension out of a file name
     */
    public static String getFileExtension(String file) {
        int lastDot = file.lastIndexOf('.');
        return lastDot == -1 ? "" : file.substring(lastDot+1);
    }

    public static String getFileExtension(File file) {
        return getFileExtension(file.getName());
    }

    /**
     * Replace file name suffix from to
     */
    public static String swapExtension(String file, String from, String to) {
        return file.substring(0, file.length()-from.length()) + to;
    }

    /**
     * Normalize a file path name, removing any irregularities
     */
    public static String normalizePath(String file) throws IOException {
        return new File(file).getCanonicalPath();
    }

    /**
     * Load properties from file into a ScriptableMap
     * @param file The file name specified relative to document root directory
     */
    public static ScriptableMap loadPropertiesFromStream(ScriptableMap map, InputStream v) throws IOException {
        Properties prop = new Properties();
        prop.load(v);

        for (String name: prop.stringPropertyNames()) {
            String p = prop.getProperty(name);
            int i = -1, j, count = 0;

            // recursively resolve and interpolate any ${...} value components, where ... is another property
            while ((i = p.indexOf("${", i + 1)) != -1 && (j = p.indexOf("}", i)) != -1) {
                String key = p.substring(i + 2, j);

                if (prop.containsKey(key))
                    p = p.substring(0, i--) + prop.get(key).toString() + p.substring(j + 1);
                else
                    i = j;

                if (count++ > 10000) // protect from possible recursion
                    throw new IOException("loadPropertiesFromStream: too many variable interpolations");
            }

            Object pt = p;

            if (p.equals("yes") || p.equals("on") || p.equals("true"))
                pt = new Boolean(true);
            else if (p.equals("no") || p.equals("off") || p.equals("false"))
                pt = new Boolean(false);

            map.put(name, pt);
        }
        return map;
    }

    public static ScriptableMap loadPropertiesFromStream(InputStream v) throws IOException {
        return loadPropertiesFromStream(new ScriptableMap(), v);
    }

    public static ScriptableMap loadProperties(ScriptableMap map, File file) throws IOException {
        return loadPropertiesFromStream(map, new FileInputStream(file));
    }

    public static ScriptableMap loadProperties(ScriptableMap map, String file) throws IOException {
        return loadProperties(map, getFile(file));
    }

    /**
     * Convert a Map to Properties object
     */
    public static Properties toProperties(Map map) {
        Properties p = new Properties();
        for (Object key: map.keySet())
            p.setProperty(key.toString(), map.get(key).toString());
        return p;
    }

    public static void makeWritableByAll(File file) throws IOException {
        file.setWritable(true, false);
        file.setReadable(true, false);
    }

    public static void makeWritableByAll(String file) throws IOException {
        makeWritableByAll(getFile(file));
    }

    /**
     * Create the destination path directories as needed, making them writable by everyone
     */
    public static void mkdirs(File file, boolean writableByAll) throws IOException {
        File dir = file.getParentFile();
        if (dir == null || dir.exists())
            return; // don't change perms on existing directories

        if (!dir.getParentFile().exists())
            mkdirs(dir, writableByAll); // fix any missing parent dirs recursively
        if (!dir.mkdir())
            throw new IOException("Can't create directory (permissions denied): " + dir);

        if (writableByAll)
            makeWritableByAll(dir);
        dir.setExecutable(true, false);
    }
}


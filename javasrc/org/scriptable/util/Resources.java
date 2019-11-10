package org.scriptable.util;

import org.scriptable.HttpRequest;
import org.scriptable.ScriptableMap;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import java.io.IOException;

final public class Resources
{
    /**
     * Send concatenated CSS files to client stripping comments and collapsing white space
     */
    public static String minifyCss(String in) throws IOException {
        StringReader reader = new StringReader(in);
        StringWriter writer = new StringWriter(in.length());
        char buf[] = new char[2048];
        int c;
        boolean space = false, spaceCarryOver = false, star = false, comment = false,
                        doubleQuoted = false, singleQuoted = false, backslash = false;
        while ((c = reader.read(buf, 0, 2048)) != -1) {
            int cc = 0;
            for (int i=0; i<c; i++) {
                char k = buf[i];
                if (!comment && !singleQuoted && (k == '"' || doubleQuoted)) { // pass double quoted unchanged
                    if (k == '"' && doubleQuoted && !backslash)
                        doubleQuoted = false;
                    else
                        doubleQuoted = true;
                    if (space) {
                        buf[cc++] = ' ';
                        buf[cc++] = k;
                        space = false;
                    }
                    else
                        buf[cc++] = k;
                    if (k == '\\')
                        backslash = !backslash;
                    else
                        backslash = false;
                    continue;
                }
                if (!comment && !doubleQuoted && (k == '\'' || singleQuoted)) { // pass single quoted unchanged
                    if (k == '\'' && singleQuoted && !backslash)
                        singleQuoted = false;
                    else
                        singleQuoted = true;
                    if (space) {
                        buf[cc++] = ' ';
                        buf[cc++] = k;
                        space = false;
                    }
                    else
                        buf[cc++] = k;
                    if (k == '\\')
                        backslash = !backslash;
                    else
                        backslash = false;
                    continue;
                }
                if (!comment && (k == ' ' || k == '\n' || k == '\r' || k == '\t')) // seen whitespace
                    space = true;
                else {
                    if (comment) {
                        if (k == '*')
                            star = true;
                        else if (star && k == '/') { // end of comment
                            comment = false;
                            star = false;
                        }
                        else
                            star = false;
                    }
                    else if (space) { // collapse the white space
                        char pc = cc == 0 ? '.' : buf[cc-1];
                        if (pc != ';' && pc != ',' && pc != '{' && pc != '}' && pc != ':' &&
                                k != '{')
                            buf[cc++] = ' '; // skip space after ; , { } : and before {
                        buf[cc++] = k;
                        space = false;
                    }
                    else if (k == '*' && cc>0 && buf[cc-1] == '/') { // start of comment
                        // it's possible that start of comment will not be detected due to /* splitting
                        // across the buffer boundary but that's OK. (Note: end of comment will always be detected)
                        --cc;
                        comment = true;
                    }
                    else
                        buf[cc++] = k;
                }
            }
            if (spaceCarryOver) {
                writer.write(' ');
                spaceCarryOver = false;
            }
            writer.write(buf, 0, cc);
            if (space) {
                spaceCarryOver = true;
                space = false;
            }
        }
        return writer.toString();
    }

    /**
     * Construct file list based on build.properties source files specification, expressed as
     * a space separated list of Java globbing patterns
     * @param basePropertyName the base name of properties specifying source file locations
     * @return List of file paths specified relative to source root, e.g.
     *          view/**.js view/**.htm
     */
    public static String[] listSourceFiles(ScriptableMap props, String basePropertyName)
        throws IOException {
        return listSourceFiles(props.get(basePropertyName + ".include", (String)null),
                props.get(basePropertyName + ".exclude", (String)null));
    }

    public static String[] listSourceFiles(String include)
        throws IOException {
        return listSourceFiles(include, null);
    }

    public static String[] listSourceFiles(String include, String exclude)
        throws IOException {
        ArrayList<String> result = new ArrayList<String>();

        PathMatcher[] matchers = getPathMatchers(include);
        PathMatcher[] dismatchers = getPathMatchers(exclude);

        ArrayList<Path> paths = new ArrayList<Path>();
        listFilesRecursively(paths, getRootPath().toFile());

        for (PathMatcher m: matchers) {

            nextPath:

            for (Path path: paths) {

                if (m.matches(path)) {

                    for (PathMatcher dm: dismatchers) {

                        if (dm.matches(path))
                            continue nextPath;
                    }

                    result.add(path.toString()); // paths already don't include hidden/emacs tmp
                }
            }
        }

        return result.toArray(new String[result.size()]);
    }

    private static FileSystem fs = FileSystems.getDefault();
    private static Path root = null;

    public static Path getRootPath() {
        if (root == null)
            root = Paths.get(HttpRequest.getDocumentRoot()).toAbsolutePath().normalize();

        return root;
    }

    public static PathMatcher[] getPathMatchers(String globList) {
        ArrayList<PathMatcher> matchers = new ArrayList<PathMatcher>();

        if (globList != null && !globList.equals("")) {

            for (String glob: splitAndTrim(globList, ' '))
                matchers.add(getPathMatcher(glob));
        }

        return matchers.toArray(new PathMatcher[0]);
    }

    public static PathMatcher getPathMatcher(String glob) {
        return fs.getPathMatcher((glob.charAt(0) == '/'? "glob:" : "glob:/") + glob);
    }

    public static boolean isPathMatching(String path, PathMatcher m) {
        return m.matches(Paths.get(path));
    }

    private static ArrayList<Path> listFilesRecursively(ArrayList<Path> result, File dir)
        throws IOException {
        List<File> list = Arrays.asList(dir.listFiles());
        int rl = getRootPath().toString().length();
        Collections.sort(list); // sort alphabetically to get repeatable order
        // list depth first, so sub-directory content is listed before the files
        for (File f: list) {
            if (f.isDirectory() && !f.isHidden()) // skip hidden directories, e.g. .git
                listFilesRecursively(result, f);
        }
        for (File f: list) {
            if (!f.isDirectory() && !f.isHidden()) {
                // Note: don't use getCanonicalPath here since it'll confuse matchers in case of symlink
                result.add(new File(f.toString().substring(rl)).toPath());
            }
        }
        return result;
    }

    public static String[] splitAndTrim(String input, char delim) {
        ArrayList<String> array = new ArrayList<String>();
        int i;
        String part;
        while (input != null) {
            i = input.indexOf(delim);
            if (i != -1) {
                part = input.substring(0, i).trim();
                input = input.substring(i + 1);
            }
            else {
                part = input.trim();
                input = null;
            }
            if (!part.equals(""))
                array.add(part);
        }
        return array.toArray(new String[array.size()]);
    }

    public static boolean waitForUpdates(String include, long timeoutSec)
        throws IOException, InterruptedException {

        final WatchService watcher = FileSystems.getDefault().newWatchService();
        HashMap<WatchKey, Path> keys = new HashMap<WatchKey, Path>();
        boolean result = false;
        String root = getRootPath().toString();
        int rootl = root.length();
        PathMatcher[] matchers = getPathMatchers(include);

        try {

System.out.println("====================");
            // NOTE: Files.walkFileTree is not suitable since it's not strictly depth-first
            // meaning it doesn't guarantee visiting directories before any of the sibling files
            ArrayList<Path> files = new ArrayList<Path>();
            listFilesRecursively(files, getRootPath().toFile());
            Path parentRegistered = null;

            for (Path file: files) {
                if (pathMatches(file, matchers)) {
                    file = new File(root + file.toString()).toPath();

                    if (Files.isSymbolicLink(file)) {
                        file = file.toRealPath().getParent();
                        WatchKey key = file.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
System.out.println("symlinked: " + file.toString());
                        keys.put(key, file);
                    }
                    else {
                        file = file.getParent();

                        if (parentRegistered == null || !file.equals(parentRegistered)) {
                            parentRegistered = file;
                            WatchKey key = file.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                                    StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                            keys.put(key, file);
System.out.println(file.toString());
                        }
                    }
                }
            }
System.out.println("====================");

//            if (watcher.poll((timeoutSec > 0? timeoutSec : 60), TimeUnit.SECONDS) != null)
//                result = true;
//            WatchKey key = watcher.poll((timeoutSec > 0? timeoutSec : 60), TimeUnit.SECONDS);
        infl:
            while (true) {

                WatchKey key = watcher.poll((timeoutSec > 0? timeoutSec : 60), TimeUnit.SECONDS);
                Path dir = keys.get(key);

                if (dir != null) {
     
                    for (WatchEvent<?> event: key.pollEvents()) {
//         
//                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE ||
//                            event.kind() == StandardWatchEventKinds.ENTRY_DELETE ||
//                            event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {

                            if (!dir.resolve((Path)event.context()).toFile().isHidden()) {
                                result = true;
                                break infl;
                            }
//                        }
                    }

                    if (!key.reset()) // keep watching this dir
                        keys.remove(key);
                }
                else
                    break infl;
            }
/*
            java.nio.file.Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                        boolean parentRegistered = false;

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException e)
                            throws IOException {
                            parentRegistered = false; // depth first...
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attr)
                            throws IOException {
                            if (file.getFileName().toString().charAt(0) == '.') // skip hidden dirs, e.g. .git
                                return FileVisitResult.SKIP_SUBTREE;
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attr)
                            throws IOException { // adding all directories to inotify watch list

//System.out.println(file.toString());
                            if (attr.isDirectory())
                                return FileVisitResult.CONTINUE;

                            Path srcPath = new File(file.toString().substring(rootl)).toPath();
                            boolean included = pathMatches(srcPath, matchers);

                            if (attr.isSymbolicLink() && included) {
                                file = file.toRealPath();
                                WatchKey key = file.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
//System.out.println("symlinked: " + file.toString());
                            }

                            if (!parentRegistered && included) {
                                file = file.getParent();
                                parentRegistered = true;
                                WatchKey key = file.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                                        StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
//System.out.println(file.toString());
                            }

                            return FileVisitResult.CONTINUE;
                        }
            });
System.out.println("====================");

            WatchKey key = watcher.poll((timeoutSec > 0? timeoutSec : 60), TimeUnit.SECONDS);
            */
/*        infl:
            while (true) {

                WatchKey key = watcher.poll((timeoutSec > 0? timeoutSec : 60), TimeUnit.SECONDS);
                Path dir = keys.get(key);

                if (dir != null) {
     
                    for (WatchEvent<?> event: key.pollEvents()) {
         
                        if (event.kind() == ENTRY_MODIFY) {
                            Path path = dir.resolve((Path)event.context());
                            path = new File(path.toString().substring(rootl)).toPath();

                            if (result = pathMatches(path, matchers))
                                break infl;
                        }
                    }

                    if (!key.reset()) // keep watching this dir
                        keys.remove(key);
                }
                else
                    break infl;
            }
            */
        }
        finally {
            if (watcher != null)
                watcher.close();
        }

        return result;
    }

    private static boolean pathMatches(Path path, PathMatcher[] matchers) {
        for (PathMatcher m: matchers) {
            if (m.matches(path)) {
                return true;
            }
        }
        return false;
    }
}


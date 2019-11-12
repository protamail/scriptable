package org.scriptable;

/**
 * The base class facilitating access to various properties if an HTTP request instance
 */

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.Part;
import javax.servlet.ServletOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.File;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.net.URL;
import java.util.Date;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.scriptable.util.Base64;
import org.scriptable.util.Files;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;

public abstract class HttpRequest {
    public final ServletContext servlet;

    private HttpServletRequest req;
    private HttpServletResponse res;

    String originalActionPath = null;
    String requestPath = null;
    String queryString = null;
    String searchString = null;
    String requestUrl = null;
    static String documentRoot = null;//Paths.get(".").toAbsolutePath().normalize() + "/";
    static String contextPath = null;
    static String cookiePath = null;
    static Boolean staticsInitialized = false;
    static String version = null;
    static boolean compilationMode = false;

    public final static int INITIAL_STATUS = 200; // if request aborted with throw, set status <> 200
    public final static String CONTENT_HTML = "text/html; charset=UTF-8";
    public final static String CONTENT_PLAIN = "text/plain; charset=UTF-8";
    static final long serverStartMillis = System.currentTimeMillis();

    static Logger infoLogger = Logger.getLogger(HttpRequest.class.getName() + ".info");
    static Logger errorLogger = Logger.getLogger(HttpRequest.class.getName() + ".error");
    static Logger accessLogger = Logger.getLogger(HttpRequest.class.getName() + ".access");
    static Logger eventLogger = Logger.getLogger(HttpRequest.class.getName() + ".event");

    static public final long instanceId = Math.round(Math.floor(Math.random()*100001));

    static ScriptableMap config = null;
    static boolean developmentMode = false;
    static boolean testEnv = false;

    static {
        try {
            ScriptableMap c = new ScriptableMap("Settings from scriptable.properties");
            Enumeration<URL> pf = ScriptableRequest.class.getClassLoader().getResources("mode.properties");

            while (pf.hasMoreElements())
                Files.loadPropertiesFromStream(c, pf.nextElement().openStream());

            // need these initialized before any other code runs
            developmentMode = c.containsKey("mode") && c.get("mode").equals("dev");
            testEnv = c.containsKey("mode") && c.get("mode").equals("test");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final static String CONFIG_FILE_NAME = "/scriptable.properties";

    static ScriptableMap loadScriptableProperties() throws IOException {
        // this is only called before global refresh, so no need to keep config references
        ScriptableMap c = new ScriptableMap("Settings from scriptable.properties");
        Files.loadProperties(c, Files.getFile(CONFIG_FILE_NAME));
        return c;
    }

    public static long getServerStartMillis() {
        return serverStartMillis;
    }

    public static void flushLoggers() {
        flushLogger(infoLogger);
        flushLogger(errorLogger);
        flushLogger(accessLogger);
        flushLogger(eventLogger);
    }

    private static void flushLogger(Logger l) {
        for (Handler h: l.getHandlers())
            h.flush();
    }

    public static String getVersion() {
        return version;
    }

    public static void setVersion(String v) {
        version = v;
    }

    public static void setCompilationMode(boolean v) {
        compilationMode = v;
    }

    private String hostContextPath = null;
    public String getHostContextPath() {
        if (hostContextPath == null)
            hostContextPath = getHostUrl() + contextPath;
        return hostContextPath;
    }

    // return the portion of the URL starting with protocol up to the beginning of path
    private String hostUrl = null;
    public String getHostUrl() {
        if (hostUrl == null) {
            hostUrl = (req.isSecure() || req.getHeader("Front-End-Https") != null?
                    "https://" : "http://") + req.getHeader("Host");
        }
        return hostUrl;
    }

    String hostRequestUrl;
    public String getHostRequestUrl() {
        if (hostRequestUrl == null)
            hostRequestUrl = getHostUrl() + getRequestUrl();
        return hostRequestUrl;
    }

    public HttpRequest(ServletContext srv, HttpServletRequest req, HttpServletResponse res)
        throws ServletException {
        servlet = srv;
        this.req = req;
        this.res = res;

        setStatus(INITIAL_STATUS);

        if (!staticsInitialized) {
            contextPath = req.getContextPath();
            cookiePath = contextPath;
            staticsInitialized = true;
            // documenRoot is being set in ScriptableServlet.init
        }

        // force initialization of some properties which are useful for async access logging
        // when request object may no longer be available
        getMethod(); getUserAgent(); isAjaxRequest();

        originalActionPath =
            // pathInfo will apply when servlet is mapped to /xxx/*
            req.getServletPath() + (req.getPathInfo() != null? req.getPathInfo() : "");
        requestPath = contextPath + originalActionPath;
    }

    String userAgent;

    public String getUserAgent() {
        if (userAgent == null)
            userAgent = req.getHeader("User-Agent");
        return userAgent == null? "" : userAgent;
    }

    // duplicate status here since response object will not be available for async tasks
    // after request finished
    int status = 0;

    public final int getStatus() {

        if (res == null)
            return status;

        return res.getStatus();
    }

    public final void setStatus(int status) {
        this.status = status;
        res.setStatus(status);
    }

    public static final String getContextPath() {
        return contextPath;
    }

    public final String getOriginalActionPath() {
        return originalActionPath;
    }

    public final String getRequestPath() {
        return requestPath;
    }

    public final String getQueryString() {
        if (queryString == null) {
            queryString = req.getQueryString();
            if (queryString == null)
                queryString = "";
        }

        return queryString;
    }

    public final String getSearch() {
        if (searchString == null) {
            searchString = getQueryString();
            if (!searchString.equals(""))
                searchString = "?" + searchString;
        }

        return searchString;
    }

    public final String getRequestUrl() {
        if (requestUrl == null)
            requestUrl = getRequestPath() + getSearch();
        return requestUrl;
    }

    public static final String getDocumentRoot() {
        return documentRoot;
    }

    public static final synchronized void setDocumentRoot(String v) throws IOException {
        documentRoot = Paths.get(v).toAbsolutePath().normalize() + "/";

        if (config == null) // load /scriptable.properties now that document root is known
            config = loadScriptableProperties();
    }

    public static final boolean isProductionMode() {
        return !developmentMode;
    }

    public static final boolean isDevelopmentMode() {
        return developmentMode;
    }

    public static final boolean isTestEnvironment() {
        return testEnv;
    }

    public static ScriptableMap getConfig() {
        return config;
    }

    String method = null;
    public final String getMethod() {
        if (method == null)
            method = req.getMethod();
        return method;
    }

    public final boolean isPostRequest() {
        return getMethod().equals("POST");
    }

    String xRequestedWith = null;

    public final boolean isAjaxRequest() {
        if (xRequestedWith == null) {
            xRequestedWith = req.getHeader("X-Requested-With");
            if (xRequestedWith == null)
                xRequestedWith = "";
        }
        return xRequestedWith.equals("XMLHttpRequest");
    }

    public final boolean isXhr() {
        return isAjaxRequest();
    }

    public static final Boolean isCompilationMode() {
        return compilationMode;
    }

    public Cookie getCookie(String name) {
        if (req == null)
            throw new RuntimeException("HTTPServletRequest object is required but not available at this time");

        Cookie cookies[] = req.getCookies();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {
                if (cookies[i].getName().equals(name)) {
                    return cookies[i];
                }
            }
        }
        return null;
    }

    public String getPlainCookieValue(String name) {
        Cookie cookie = getCookie(name);
        if (cookie != null)
            return cookie.getValue();
        return null;
    }

    public void setCookiePath(String path) {
        cookiePath = path;
    }

    public void removeCookie(String name) {
        clearCookie(name);
    }

    public void clearCookie(String name) {
        clearCookie(name, cookiePath);
    }

    public void clearCookie(String name, String path) {
        Cookie cookie = getCookie(name);
        if (cookie != null) {
            cookie.setValue("");
            cookie.setMaxAge(0);
            cookie.setPath(path);
            if (!isCommitted())
                res.addCookie(cookie);
        }
    }

    public void setPlainCookie(String name, String value, int expireSec) {
        setPlainCookie(name, value, expireSec, false, false);
    }

    public void setPlainCookie(String name, String value, int expireSec, boolean httpOnly, boolean secure) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath(cookiePath);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        if (expireSec > 0)
            cookie.setMaxAge(expireSec);
        if (!isCommitted()) {
            res.addCookie(cookie);
        }
    }

    public void setBase64Cookie(String name, String value, int expireSec) {
        setBase64Cookie(name, value, expireSec, false, false);
    }

    public void setBase64Cookie(String name, String value, int expireSec, boolean httpOnly, boolean secure) {
        setBase64Cookie(name, value.getBytes(), expireSec, httpOnly, secure);
    }

    public void setBase64Cookie(String name, double value, int expireSec, boolean httpOnly, boolean secure) {
        setBase64Cookie(name, ByteBuffer.wrap(new byte[8]).putDouble(value).array(),
                expireSec, httpOnly, secure);
    }

    public void setBase64Cookie(String name, byte[] value, int expireSec, boolean httpOnly, boolean secure) {
        setPlainCookie(name, Base64.encode(value).replace("\n", ""), expireSec, httpOnly, secure);
    }

    public String getBase64CookieValue(String name) {
        Cookie cookie = getCookie(name);
        if (cookie != null)
            return new String(Base64.decode(cookie.getValue()));
        return null;
    }

    public Double getBase64CookieAsDouble(String name) {
        Cookie cookie = getCookie(name);
        if (cookie != null)
            return ByteBuffer.wrap(Base64.decode(cookie.getValue())).getDouble();
        return null;
    }

    /**
     * Log miscelaneous messages for debugging and general information purposes
     */
    public static void log(String msg) {
        logError(msg);
    }

    /**
     * Log miscelaneous messages for debugging and general information purposes
     */
    public static void logInfo(String msg) {
        if (isCompilationMode() || infoLogger == null)
            System.err.println(msg);
        else if (infoLogger != null)
            infoLogger.log(Level.INFO, msg);
    }

    /**
     * Log error messages
     */
    public static void logError(String msg) {
        if (isCompilationMode() || errorLogger == null)
            System.err.println(msg);
        else if (errorLogger != null) {
            errorLogger.log(Level.SEVERE, msg);
            // NOTE: stdout is not going to catalina.out in e.g. fedora
            //System.err.println(msg); // dup errors in catalina.out
        }
    }

    /**
     * Log warning messages
     */
    public static void logWarning(String msg) {
        if (isCompilationMode() || errorLogger == null)
            System.err.println(msg);
        else if (errorLogger != null)
            errorLogger.log(Level.WARNING, msg);
    }

    /**
     * Log user access for later parsing and reporting
     */
    public static void logAccess(String msg) {
        if (isCompilationMode() || accessLogger == null)
            System.err.println(msg);
        else if (accessLogger != null)
            accessLogger.log(Level.INFO, msg);
    }

    /**
     * Log important events for later parsing and reporting
     */
    public static void logEvent(String msg) {
        if (isCompilationMode() || eventLogger == null)
            System.err.println(msg);
        else if (eventLogger != null)
            eventLogger.log(Level.INFO, msg);
    }

    public static String getStackTrace(Throwable e) {
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        e.printStackTrace(printer);
        printer.flush();
        return writer.toString();
    }

    public static Exception getRootCauseException(Exception e) {
        Exception cause = e;
        int rec = 100;
        // NOTE: we don't unwrap SQLException because it's being augmented with SQL info in Jdbc
        while (!(cause instanceof SQLException) && cause.getCause() instanceof Exception && rec-- > 0)
            cause = (Exception) cause.getCause();
        if (rec == 0)
            throw new RuntimeException("Cause too deep!");
        return cause;
    }

    /**
     * Generate formatted error source context snippet
     */
    public static String getSourceSnippet(String file, int line, int contextLines) {
        if (file != null) {
            try {
                if (file.startsWith("./"))
                    file = getDocumentRoot() + "/." + file; // for js files pre-compiled to class
                BufferedReader fr = new BufferedReader(new FileReader(file));
                StringWriter writer = new StringWriter();
                PrintWriter snippet = new PrintWriter(writer);
                String cl;
                for (int l=1; l<=line+contextLines && (cl = fr.readLine())!=null; l++) {
                    if (l >= line-contextLines) {
                        snippet.format("%-7d", l);
                        snippet.print(l == line ? '>' : ' ');
                        snippet.println(cl);
                    }
                }
                return writer.toString();//.replace("<",">");
            }
            catch (IOException e) {
            }
        }
        return "\n";
    }

    public ServletContext getScriptableServlet() {
        return servlet;
    }

    long contentLength = 0L;
    public void setContentLength(long v) {
        contentLength = v;
    }

    public final HttpServletResponse getHttpServletResponse() {
        return res;
    }

    public final HttpServletRequest getHttpServletRequest() {
        return req;
    }

    public final void clearHttpServletRequestResponse() {
        status = getStatus(); // save status for async tasks reference
        req = null;
        res = null;
    }

    public long getContentLength() {
        return contentLength;
    }

    public final ServletOutputStream getOutputStream() throws IOException {
        return res.getOutputStream();
    }

    public final PrintWriter getWriter() throws IOException {
        return res.getWriter();
    }

    public final boolean isCommitted() {
        return res.isCommitted();
    }

    public final String getHeader(String name) {
        return req.getHeader(name);
    }

    public final long getDateHeader(String name) {
        return req.getDateHeader(name);
    }

    public final boolean containsResponseHeader(String name) {
        return res.containsHeader(name);
    }

    public final void setAttribute(String name, Object value) {
        req.setAttribute(name, value);
    }

    public final void setHeader(String name, String value) {
        res.setHeader(name, value);
    }

    public final void setDateHeader(String name, long value) {
        res.setDateHeader(name, value);
    }

    public final void sendRedirectResponse(String location) throws IOException {
        res.sendRedirect(location);
    }

    public final Enumeration<String> getParameterNames() {
        return req.getParameterNames();
    }

    public final String[] getParameterValues(String name) {
        return req.getParameterValues(name);
    }

    public final String getParameter(String name) {
        return req.getParameter(name);
    }

    public final Cookie[] getRequestCookies() {
        return req.getCookies();
    }

    public final Collection<Part> getRequestParts() throws IOException, ServletException {
        return req.getParts();
    }

    public final Part getRequestPart(String name) throws IOException, ServletException {
        return req.getPart(name);
    }

    public final String getRequestContentType() {
        return req.getContentType();
    }

    public final String getResponseContentType() {
        return res.getContentType();
    }

    public final void setContentType(String type) {
        res.setContentType(type);
    }

    public final void setCharacterEncoding(String encoding) {
        res.setCharacterEncoding(encoding);
    }

    public final void resetBuffer() {
        res.resetBuffer();
    }

    public void sendString(String data) throws IOException {
        setContentLength(data.length());
        getWriter().print(data);
    }

    public void sendTextFile(File file) throws IOException {
        setContentLength(file.length());
        Files.copyStream(new FileReader(file), getWriter());
    }

    public void sendTextFile(String file) throws IOException {
        sendTextFile(Files.getFile(file));
    }

    public void sendBinaryFile(File file) throws IOException {
        setContentLength(file.length());
        Files.copyStream(new FileInputStream(file), getOutputStream());
    }

    public void sendBinaryFile(String file) throws IOException {
        sendBinaryFile(Files.getFile(file));
    }

    public void sendBlob(java.sql.Blob blob) throws IOException, SQLException {
        setContentLength(blob.length());
        Files.copyStream(blob.getBinaryStream(), getOutputStream());
    }

    public void sendBlob(byte[] data) throws IOException {
        setContentLength(data.length);
        getOutputStream().write(data);
    }

    public void sendBase64Blob(String data) throws IOException {
        byte bytes[] = Base64.decode(data);
        setContentLength(bytes.length);
        getOutputStream().write(bytes);
    }

    public static ExecutorService createCachedThreadPool() {
        ExecutorService es = Executors.newCachedThreadPool();
        ScriptableContextListener.registerThreadPool(es);
        return es;
    }

    public static ExecutorService createFixedThreadPool(int capacity) {
        ExecutorService es = Executors.newFixedThreadPool(capacity);
        ScriptableContextListener.registerThreadPool(es);
        return es;
    }

    public static ScheduledExecutorService createSingleThreadScheduledExecutor() {
        ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
        ScriptableContextListener.registerThreadPool(es);
        return es;
    }

    public static void destroyThreadPool(ExecutorService es) {
        ScriptableContextListener.unregisterThreadPool(es);
        es.shutdownNow();
    }

    public static long getCurrentTime() {
        return new Date().getTime();
    }
}


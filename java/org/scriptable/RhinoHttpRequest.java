package org.scriptable;

/**
 * The Rhino javascript runtime access
 *
 * This class is used to process HTTP requests using javascript code
 */

import org.scriptable.util.Files;
import org.scriptable.util.Json;
import org.scriptable.util.Jdbc;
import org.scriptable.util.Resources;
import org.scriptable.template.TemplateUtil;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.WrapFactory;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptStackElement;
import org.mozilla.javascript.Undefined;
import java.io.FileReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Locale;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.text.DecimalFormat;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.Part;

import javax.servlet.ServletException;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.WrappedException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public final class RhinoHttpRequest extends HttpRequest implements Callable {
    Thread requestThread = null;
    Throwable asyncException = null;
    private static ScriptableMap config;
    static final boolean developmentMode;
    static final boolean testEnv;

    static {
        config = loadScriptableProperties();
        developmentMode = config.containsKey("mode") && config.get("mode").equals("dev");
        testEnv = config.containsKey("mode") && config.get("mode").equals("test");
    }

    // The names of bootstrap and configuration files
    public final static String INIT_JS = "scriptable/core/_init.js";
    public final static String CONFIG_FILE_NAME = "/scriptable.properties";
    public final static String CONTEXT_PATH = "__contextPath__";
    public final static String CONTEXT_MODULE = "__contextModule__";
    public final static String CONTEXT_EXPORTS_OBJ = "__contextExportsObj__";

    public final static String CONF_TARGETS_PROP = "targets";

    public static Function startFunc = null;
    static Function requireFunc = null;
    public static Scriptable scriptableExports = null;
    public Function errorHandler = null;
    public Function dispatchOn = null;
    public Scriptable actionScopeStack = null;
    public boolean skipErrorLogging = false; // set to true to skip error logging in this request

    private static ScriptableMap loadScriptableProperties() {
        ScriptableMap c = new ScriptableMap("Settings from scriptable.properties");

        try {
            Enumeration<URL> pf = RhinoHttpRequest.class.getClassLoader()
                .getResources("scriptable.properties");

            while (pf.hasMoreElements())
                Files.loadPropertiesFromStream(c, pf.nextElement().openStream());

            pf = RhinoHttpRequest.class.getClassLoader().getResources("mode.properties");

            while (pf.hasMoreElements())
                Files.loadPropertiesFromStream(c, pf.nextElement().openStream());
        }
        catch (IOException e) {
            logError("loadScriptableProperties: " + e.getMessage());
        }

        return c;
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

    private final static ThreadLocal<RhinoHttpRequest> requestInstance = new ThreadLocal<RhinoHttpRequest>();
    private final static ThreadLocal<Locale> locale = new ThreadLocal<Locale>();

    /**
     * Custom WrapFactory to avoid wrapping some java objects when they are passed to javascript realm
     */
/*    static class PrimitiveWrapFactory extends WrapFactory {
        @Override public Object wrap(Context cx, Scriptable scope, Object obj, Class<?> staticType) {
            return super.wrap(cx, scope, obj, staticType);
        }
    }

    static PrimitiveWrapFactory myWrapFactory = new PrimitiveWrapFactory();
*/
    static Scriptable globalScope = null;
    // make older than non-existent file
    static long initLastModified = -1, configPropertiesLastModified = -1;

    public RhinoHttpRequest(ServletContext srv, HttpServletRequest req, HttpServletResponse res)
        throws ServletException {
        super(srv, req, res);
    }

    boolean quietOn404 = false;

    public RhinoHttpRequest keepQuietOn404() {
        quietOn404 = true;

        return this;
    }

    public static Scriptable getGlobalScope() {
        return globalScope;
    }

    public static void sealGlobalScope() {
//        globalScope.seal();
    }

    public static void unsealGlobalScope() {
//        globalScope.unseal();
    }

    // emptyScope instanceof Object must be true
    static Scriptable emptyScope = null;

    public static Scriptable getEmptyScope() {

        if (emptyScope == null && globalScope != null) {
            emptyScope = Context.getCurrentContext().newObject(globalScope);
            ((ScriptableObject)emptyScope).sealObject();
        }

        return emptyScope;
    }

    /**
     * Wrap server JS module script in anonymous function
     */
    public static String wrapJsModule(String script) {
        //return "(function(){ return function(exports){'use strict';" + script + "}})();";
        // loading parameter is true when reloading the module as opposed to just re-evaluating moduleFunc
        return "(function(exports, loading){'use strict'; var module = {exports: exports};" + script + "})";
    }

    public static String getGenClassName(String file) throws IOException {
        file = Files.normalizePath("/" + file);
        return "org.scriptable.gen." + file.replaceAll("\\W", "_");
    }

    /**
     * Evaluate javascript file
     * @param file Pathname of the file to evaluate is specified relative to the script base
     */
    public static Object evaluateFile(String file, String contextPath, Scriptable exports)
        throws IOException {

        synchronized (globalScope) {
            Object savedRequirePath = globalScope.get(CONTEXT_PATH, globalScope);
            Object savedRequireModule = globalScope.get(CONTEXT_MODULE, globalScope);
            Object savedExportsObj = globalScope.get(CONTEXT_EXPORTS_OBJ, globalScope);
            unsealGlobalScope();
            globalScope.put(CONTEXT_PATH, globalScope, contextPath);
            globalScope.put(CONTEXT_MODULE, globalScope, file);
            globalScope.put(CONTEXT_EXPORTS_OBJ, globalScope, exports);
            sealGlobalScope();
            // protect global scope from concurrent modification
            // recursion should still work
            Object moduleFunction = null;
            Context ctx = Context.getCurrentContext();

            try {

                if (Files.getFile(file).exists()) {

                    if (!isCompilationMode())
                        logEvent("loading (from source) " + file);

                    String script = wrapJsModule(Files.getFileAsString(file));
                    file = Files.normalizePath(getDocumentRoot() + file);

                    moduleFunction = ctx.evaluateString(globalScope, script, file, 1, null);
                }
                else { // if script file doesn't exist, try loading precompiled class for it

                    if (!isCompilationMode())
                        logEvent("loading (from class) " + file);

                    try {
                        moduleFunction = ((Function)Class.forName(getGenClassName(file))
                            .getDeclaredConstructor().newInstance())
                            .call(ctx, globalScope, globalScope, null);
                    }
                    catch (ClassNotFoundException e) {
                        throw new IOException("evaluateFile: neither " + file + " nor pre-compiled class " +
                                getGenClassName(file) + " exists.");
                    }
                }

                ((Function)moduleFunction).call(ctx, globalScope, globalScope,
                    new Object[] { exports, true }); // "loading" moduleFunc parameter == true
            }
            catch (Throwable e) {
                //logError(getStackTrace(e));
                throw new WrappedException(e);
            }
            finally {
                unsealGlobalScope();
                globalScope.put(CONTEXT_PATH, globalScope, savedRequirePath); // needed to handle recursive require calls
                globalScope.put(CONTEXT_MODULE, globalScope, savedRequireModule);
                globalScope.put(CONTEXT_EXPORTS_OBJ, globalScope, savedExportsObj);
                sealGlobalScope();
            }

            return moduleFunction;
        }
    }

    /**
     * Call a javascript function
     * @param name function name specification, e.g. a.b.func
     * @param param variable list of function arguments
     * @return object returned by the called function
     */
    public static Object callJsFunction(String name, Object... param) {
        Scriptable scope = getGlobalScope();
        Function f = resolveJsFunction(scope, name);

        if (f == null)
            throw new RuntimeException("Call to undefined Javascript function: " + name);

        return callJsFunction(f, param);
    }

    public static Function resolveJsFunction(Scriptable scope, String name) {
        Object result = null;

        if (name.indexOf(".") == -1)
            result = scope.has(name, scope) ? scope.get(name, scope) : null;
        else {
            String [] scopes = name.split("\\.");
            for (int i=0; i<scopes.length && scope != null; i++) {
                result = scope.has(scopes[i], scope) ? scope.get(scopes[i], scope) : null;
                scope = result instanceof Scriptable? (Scriptable) result : null;
            }
        }

        return result instanceof Function? (Function) result : null;
    }

    public static Object callJsFunction(Function func, Object...param) {
        Scriptable scope = func.getParentScope();

        if (scope == null)
            throw new RuntimeException(
                    "callJsFunction: must be called from JS context, use runInJsEnv otherwise");

        if (param == null) // received single parameter value of null
            param = new Object[] { null };

        return func.call(Context.getCurrentContext(), scope, scope, param);
    }

    public static Object callJsFunction(String moduleName, String funcName, Object... param) {

        if (requireFunc == null) {
            requireFunc = resolveJsFunction(getGlobalScope(), "require");
            if (requireFunc == null)
                throw new RuntimeException("Can't resolve 'require' function in JS runtime");
        }

        Object module = callJsFunction(requireFunc, moduleName);

        if (module == null)
            throw new RuntimeException("Can't resolve module: " + moduleName);

        Function jsFunction = resolveJsFunction((Scriptable)module, funcName);

        if (jsFunction == null)
            throw new RuntimeException("Can't resolve '" + funcName + "' function in " + moduleName);

        return callJsFunction(jsFunction, param);
    }

    public static RhinoHttpRequest getCurrentRequest() {
        RhinoHttpRequest r = requestInstance.get();

        if (r == null)
            throw new RuntimeException("RhinoHttpRequest object is not available.");

        return r;
    }

    public static Locale getLocale() {
        Locale l = locale.get();

        if (l == null) {
            l = Locale.US; // default locale (can be customized per request)
            locale.set(l);
        }

        return l;
    }

    public static void setLocale(Locale l) {
        locale.set(l);
    }

    /**
     * Used for timing various portions of request in test env
     */
    static final class RequestTimerCheckpoint {
        public final double time;
        public final String label;

        RequestTimerCheckpoint(double time, String label) {
            this.time = time;
            this.label = label;
        }
    };

    ArrayList<RequestTimerCheckpoint> requestTimerCheckpoints = null;

    public void setRequestTimerCheckpoint(String label) {

        if (requestTimerCheckpoints == null)
            requestTimerCheckpoints = new ArrayList<RequestTimerCheckpoint>();

        requestTimerCheckpoints.add(new RequestTimerCheckpoint(System.nanoTime()/1000000., label));
    }

    long requestTime = 0, requestStartNano = 0;

    /**
     * The timestamp of this request
     */
    public long getRequestTime() {
        return requestTime;
    }

    /**
     * User ID to be logged in access log
     */
    String accessLogUserId = null;

    public void setAccessLogUserId(String v) {
        accessLogUserId = v;
    }

    public String getAccessLogUserId() {
        return accessLogUserId;
    }

    /**
     * Get current RhinoHttpRequest instance
     */
    public static RhinoHttpRequest getCurrentInstance() {
        return getCurrentRequest();
    }

    /**
     * Used for logging access
     */
    public double getRequestLengthMillis() {
        return Math.round((System.nanoTime() - requestStartNano)/1000000.);
    }

    /**
     * Run code in javascript environment
     */
    public static Object runInJsEnv(RhinoHttpRequest request, Callable s) throws Exception {
        requestInstance.set(request);
        setLocale(Locale.US); // reset to default locale
        Context cx = ContextFactory.getGlobal().enterContext();
        cx.setOptimizationLevel(9); // use maximum optimization level
        cx.setLanguageVersion(Context.VERSION_ES6);
//        cx.setWrapFactory(myWrapFactory);
        cx.getWrapFactory().setJavaPrimitiveWrap(false); // no need for myWrapFactory

        try {

            if (request != null)  // can be null e.g. build process
                request.lockJS(); // prevent async tasks from running their JS portions concurrently

            if (globalScope == null) {

                synchronized(RhinoHttpRequest.class) {

                    if (globalScope == null) {

                        if (!isCompilationMode())
                            logEvent("Initializing global scope");

                        // create non-sealed global scope
                        Scriptable std = cx.initStandardObjects(null, false);
                        // NOTE: Rhino prevents global undef read/write in strict mode
                        globalScope = std;//new ScriptableMap(std);
                        globalScope.setParentScope(null);
//                        globalScope.setObjectName("global scope");
                        // evaluate in the global scope
                        globalScope.put(CONTEXT_PATH, globalScope, null);
                        globalScope.put(CONTEXT_MODULE, globalScope, INIT_JS);
                        globalScope.put(CONTEXT_EXPORTS_OBJ, globalScope, null);
                        evaluateFile(INIT_JS, INIT_JS.substring(0, INIT_JS.lastIndexOf('/') + 1),
                                null /* do not allow exports from main module */);
                        sealGlobalScope();
                    }
                }
            }

            return s.call(); // execute JS related code in single-threaded (per request) environment
        }
        finally {

            if (request != null)
                request.unlockJS();

            requestInstance.remove();
            // let threadLocalPerTaskCleaner cleanup connections cached per DS per thread
            //Jdbc.closeAll(); // close any cached jdbc connections on the current thread
            threadLocalPerTaskCleaner.doCleanup();
            Context.exit(); // recycle rhino context
        }
    }

    public static Object runInJsEnv(final RhinoHttpRequest r, final Function func, final Object... param)
        throws Exception {

        return runInJsEnv(r, ()-> {
            return callJsFunction(func, param);
        });
    }

    public Object runInJsEnv(final Function func, final Object... param) throws Exception {
        return runInJsEnv(this, func, param);
    }

    static String describeRhinoException(RhinoException rhinoException) {
        Exception cause = getRootCauseException(rhinoException);
        String soyFilePath = null;
        String msg = "";
        RhinoException re = rhinoException;

        if (cause instanceof RhinoException && ((RhinoException)cause).sourceName() != null)
            re = (RhinoException)cause;

        ScriptStackElement[] ss = rhinoException.getScriptStack();

        ScriptStackElement userCode = null;

        for (ScriptStackElement sse: ss) { // try finding user code for detailed snippet

            if (sse.fileName != null) {
                userCode = sse;
                break;
            }
        }

        if (userCode != null && userCode.fileName != null) {
            msg = "at " + userCode.fileName + ":\n\n" +
                getSourceSnippet(userCode.fileName, userCode.lineNumber, 5) + "\n";
        }
        else if (re.sourceName() != null) {
            msg = "at " + re.sourceName() + ":\n\n" +
                getSourceSnippet(re.sourceName(), re.lineNumber(), 5) + "\n";
        }

        for (ScriptStackElement sse: ss) {
            msg += "at " + sse.fileName + ":\n";
            msg += getSourceSnippet(sse.fileName, sse.lineNumber, 0) + "\n";
        }

        msg += getStackTrace(cause);

        return msg;
    }

    boolean isSuccess = false;

    public boolean isSuccess() {
        return isSuccess;
    }

    /**
     * Handle incoming HTTP requests by passing control to javascript entry point
     */
    public void handleRequest() throws ServletException {
        String result = null;
        requestThread = Thread.currentThread();
        requestStartNano = System.nanoTime();

        try {

            if (isDevelopmentMode() && (
                        initLastModified < Files.getLastModified(INIT_JS) ||
                        // properties affect group import defs and scriptable.js module
                        configPropertiesLastModified < Files.getLastModified(CONFIG_FILE_NAME))) {

                globalScope = null;
                emptyScope = null;
                startFunc = null;
                r = null;
		requireFunc = null;
                initLastModified = Files.getLastModified(INIT_JS);
                configPropertiesLastModified = Files.getLastModified(CONFIG_FILE_NAME);
                config = loadScriptableProperties();
            }

            result = runInJsEnv(this, this).toString();

            if (!isCommitted()) {

                if (getStatus() == INITIAL_STATUS)
                    setStatus(200);

                if (result != null && !result.equals(""))
                    Files.copyStream(new StringReader(result), getWriter());
            }

            // must be the last line in the block
            isSuccess = true;
        }
        catch (Exception e) { // RhinoException|InterruptedException|RuntimeException

            if (getStatus() == HttpServletResponse.SC_NOT_FOUND ||
                    e instanceof InterruptedException) {
                // in case interruption exception was generated by async task
                setStatus(HttpServletResponse.SC_NOT_FOUND);
                sendErrorResponse(e, null); // return nice 404 page
                return;
            }
            else if (e instanceof InterruptedException) {
                // ignore interruption exceptions generated by async tasks
                return;
            }

            RhinoException rhinoException = null;

            if (asyncException != null) {

                if (asyncException instanceof RhinoException)
                    rhinoException = (RhinoException)asyncException;

                else {
                    sendErrorResponse(asyncException, getStackTrace(e));
                    return;
                }
            }
            else if (e instanceof RhinoException) {
                rhinoException = (RhinoException)e;
            }
            else {
                sendErrorResponse(e, getStackTrace(e));
                return;
            }

            if (!isCommitted() && getResponseContentType() == null)
                setContentType(CONTENT_PLAIN); // avoid "no element found" error in FF

            // if status code has been changed,
            // assume request was already finalized and aborted with a throw
            if (getStatus() != INITIAL_STATUS)
                return;

            sendErrorResponse(getRootCauseException(rhinoException), describeRhinoException(rhinoException));
        }
        catch (NoClassDefFoundError e) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            sendErrorResponse(e, "ClassLoader: " + cl + "\n" + getStackTrace(e));
        }
        catch (Throwable e) {
            sendErrorResponse(e, getStackTrace(e));
        }
        finally {
            requestThread = null; // prevent any outstanding async tasks from interrupting this thread
            clearHttpServletRequestResponse();

            afterRequestQueue.push(()-> { // don't wait for log flush to complete before finalizing request
                flushLoggers();

                return null;
            });

            afterRequestQueue.flush(); // now move afterRequestQueue to the real queue to start processing
        }
    }

    // Post request jobs are scheduled to run on a separate thread right after request was completed.
    // They are designed to improve response time by postponing non-critical request related tasks
    // until after request has been completed
    private final static JobQueue _afterRequestQueue = new JobQueue(1000);
    public final JobQueueBuffer afterRequestQueue = new JobQueueBuffer(this, _afterRequestQueue);

    public final static ThreadLocalPerTaskCleaner threadLocalPerTaskCleaner = new ThreadLocalPerTaskCleaner();

    public static ExecutorService asyncPool = null;
    private final ReentrantLock jsLock = new ReentrantLock(); // prevents concurrent JS evaluation

    public void lockJS() throws InterruptedException {
        jsLock.lockInterruptibly();
    }

    public void unlockJS() {

        while (jsLock.getHoldCount() > 0)
            jsLock.unlock(); // let any unfinished async tasks to continue
    }

    public void sleep(long millis) throws InterruptedException {
        unlockJS();
        Thread.currentThread().sleep(millis);
        lockJS();
    }

    public static boolean isOnMainThread() {
        return requestInstance.get() != null;
    }

    public interface Completion {
        public void await() throws InterruptedException, ExecutionException;
    }

    public Completion callJsFunctionAsync(ExecutorService pool, final Function func, final Object... param)
        throws InterruptedException {

        if (pool == null) {

            if (asyncPool == null) {

                synchronized (RhinoHttpRequest.class) {

                    if (asyncPool == null)
                        asyncPool = createCachedThreadPool();
                }
            }

            pool = asyncPool;
        }
//        final Condition asyncTaskHasAcquiredLock = jsLock.newCondition();
        final RhinoHttpRequest r = this;

        final Future task = pool.submit(() -> {

            try {
//                    asyncTaskHasAcquiredLock.signal(); // let parent thread continue
                r.runInJsEnv(func, param);
            }
            catch (Throwable e) {

                if (r.asyncException == null)
                    r.asyncException = e; // let the request thread report the exception

                if (r.requestThread != null)
                    r.requestThread.interrupt();
            }
        });
        // wait for the task to acquire jsLock (NOTE: await releases/reacquires the lock on entry/return)
// doesn't seem to improve performance compared to no wait
//        asyncTaskHasAcquiredLock.await(5, TimeUnit.SECONDS);
        return new Completion() {
            @Override
            public void await() throws InterruptedException, ExecutionException {
                unlockJS();
                task.get(); // wait for task completion while not holding the lock
                lockJS();
            }
        };
    }

    @Override
    public Object call() throws Exception {
        String result = "";
        requestTime = System.currentTimeMillis();

        if (isDevelopmentMode() || isTestEnvironment())
            setRequestTimerCheckpoint("started");

        setCharacterEncoding("UTF-8");

        // relative paths are not allowed to prevent unintended files from being served
        if (originalActionPath.indexOf("..") != -1) {
            setStatus(HttpServletResponse.SC_NOT_FOUND);
            throw new InterruptedException();
        }

        if (startFunc == null)
            throw new RuntimeException("startFunc was not defined");

        result = callJsFunction(startFunc, getNativeCurrentRequest()).toString();

        if (getStatus() == HttpServletResponse.SC_NOT_FOUND)
            throw new InterruptedException();

        if (!isCommitted()) {

            if (getStatus() == INITIAL_STATUS)
                setStatus(200);

            if (getResponseContentType() == null)
                setContentType(CONTENT_HTML); // avoid "no element found" in FF

            if (result != null && !result.equals("")) {

                // set cache control headers only if non-empty result
                if (!containsResponseHeader("Cache-Control")) {
                    setHeader("Expires", "-1");
                    setHeader("Cache-Control", "max-age=0, private");
                }

                if (!containsResponseHeader("Last-Modified"))
                    setDateHeader("Last-Modified", requestTime);
            }
        }

        return result;
    }

    // This object is used to make functions exported by scriptable library be accessible via r object
    // It will also make access to non-existent property in r raise an error
    NativeJavaObject r = null;

    NativeJavaObject getNativeCurrentRequest() {

        if (r == null) {
            r = new NativeJavaObject(globalScope, this, this.getClass()) {

                @Override
                public boolean has(String name, Scriptable start) {
                    return scriptableExports != null && scriptableExports.has(name, start) || super.has(name, start);
                }

                @Override
                public Object get(String name, Scriptable start) {
                    // it's cheaper to check scriptable exports first, and since r is mostly functional
                    // interface, undef read is going to be caught anyway
                    return scriptableExports != null && scriptableExports.has(name, start)?
                        scriptableExports.get(name, start) : super.get(name, start);
//                    Object result = super.get(name, start);
//                    if (result == Scriptable.NOT_FOUND)
//                        result = scriptableExports.get(name, start);
//                    return result;
                }
            };
        }

        return r;
    }

    // shortcut methods to access magic r variable from static scope
    public static NativeJavaObject getR() { // scriptable request object
        return getCurrentRequest().getNativeCurrentRequest();
    }

    static NativeJavaClass _r = null;

    // get static variant of magic r variable
    public static NativeJavaClass get_r() { // scriptable request class

        if (_r == null) {
            _r = new NativeJavaClass(globalScope, RhinoHttpRequest.class) {

                @Override
                public boolean has(String name, Scriptable start) {
                    return scriptableExports != null && scriptableExports.has(name, start) || super.has(name, start);
                }

                @Override
                public Object get(String name, Scriptable start) {
                    return scriptableExports != null && scriptableExports.has(name, start)?
                        scriptableExports.get(name, start) : super.get(name, start);
                }
            };
        }

        return _r;
    }

    public Scriptable getP() { // request parameter object
        return getParam();
    }

    public ScriptableMap getT() { // template scope object
        return getTemplateParam();
    }

    public String getRequestTimings() {
        StringBuilder buf = new StringBuilder();
        setRequestTimerCheckpoint("done");
        DecimalFormat df = new DecimalFormat("#.#");
        buf.append(df.format(getRequestLengthMillis()) + "ms = ");

        if (requestTimerCheckpoints.size() > 2) {

            for (int i=1, l=requestTimerCheckpoints.size(); i<l; i++) {

                if (i > 1)
                    buf.append(" + ");

                buf.append(df.format(requestTimerCheckpoints.get(i).time-requestTimerCheckpoints.get(i-1)
                            .time));
                buf.append(" ");
                buf.append(requestTimerCheckpoints.get(i).label);
            }
        }

        return buf.toString();
    }

    public void sendErrorResponse(Throwable e, String errorDetails) {

        // if RhinoFilter is used to handle some of the requests, while letting others to be
        // handled down the filter chain, -- it may be necessary to not send anything to the client
        // in case of error, otherwise IllegalStateException may result (getOutputStream vs getWriter)
        if (quietOn404 && getStatus() == HttpServletResponse.SC_NOT_FOUND)
            return;

        // if status code has already been changed, and it's not 404 (message generated below)
        // assume request was already finalized and aborted with a throw
        if (getStatus() != INITIAL_STATUS && getStatus() != HttpServletResponse.SC_NOT_FOUND)
            return;

        String errorMsg = getLogErrorMessage(e, errorDetails); // could log error

        try {

            // now that error has been logged, try to send it to client
            if (!isCommitted()) {
                resetBuffer();

                if (getResponseContentType() == null)
                    setContentType(CONTENT_PLAIN);

                if (getStatus() == INITIAL_STATUS)
                    setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

                getWriter().print(errorMsg);
            }
        }
        catch (Exception ie) {}
    }

    static Object getJavaScriptExceptionObject(JavaScriptException e) {
        Object o = e.getValue();

        while (o instanceof JavaScriptException)
            o = ((JavaScriptException)e).getValue();

        if (o instanceof NativeJavaObject)
            o = ((NativeJavaObject)o).unwrap();

        return o;
    }

    public String getUpdateWatcherText() {
        // will connect active tab to websocket backend and reload whenever "reload" command is received
        // from backend or backend instanceId has changed
        return "var i; (function() {(function f() { if (document.hidden) { setTimeout(f, 1000); return } var w = new WebSocket((location.protocol == 'http:'? 'ws://' : 'wss://') + location.host + '" +
            getContextPath() + "' + '/updateWatcher'); w.onmessage = function(e) { if (e.data == 'reload') location.reload(); else if (e.data.indexOf('instanceId=') == 0) { if (i && i != e.data) location.reload(); else { i = e.data; console.log(i) } } }; w.onclose = function(e) { setTimeout(f, 1000) } })()})();";
    }

    public String getLogErrorMessage(Throwable e, String errorDetails) {
        String result = null;
        Object customErrorMessage = null;

        try {
            Object jsev = null;

            if (e instanceof JavaScriptException) // extract actual java exception
                jsev = getJavaScriptExceptionObject((JavaScriptException)e);

            // NOTE: no logs if custom errorHandler returns result or "page not found"
            if (errorHandler != null && config != null) {

                try {
                    Object error = jsev == null? e : jsev;
                    customErrorMessage = runInJsEnv(errorHandler, getNativeCurrentRequest(),
                            // make sure js "instanceof" recognizes object class for what it is
                            // wrapFactory doesn't wrap Scriptable object types
                            new NativeJavaObject(globalScope, error, error.getClass()));
                }
                catch (Throwable jse) {
                    jsev = null; // ignore original js exception since there's one in the custom handler itself
                    if (jse instanceof JavaScriptException) // extract actual java exception
                        jsev = getJavaScriptExceptionObject((JavaScriptException)jse);

                    if (jse instanceof RhinoException)
                        errorDetails = describeRhinoException((RhinoException) jse);

                    e = jse;

                    if (!isCommitted())
                        setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }

                if (customErrorMessage == Undefined.instance)
                    customErrorMessage = null;
            }

            String homeUrl = (config != null && config.containsKey("host_url")? getHostUrl() : "") +
                getContextPath();

            if (!isCommitted())
                setContentType(CONTENT_HTML);

            if (customErrorMessage == null && getStatus() == HttpServletResponse.SC_NOT_FOUND) {
                return "<!doctype html><html><body style=\"margin:100px;\"><p style=\"font-size:62px; " +
                    "margin-bottom:100px;\">404<br>Page not found</p><a href=\"" + homeUrl +
                    "\" style=\"margin-top: 200px;\">" + homeUrl + "</a>" + (isDevelopmentMode()?
                            "<script>" + getUpdateWatcherText() + "</script>" : "") + "</body></html>";
            }

            String msg = e.getMessage();

            if (jsev != null && !jsev.equals(""))
                msg = Json.getObjectId(jsev) + ": " + Json.describe(jsev);

            String errorMsg = (msg == null? "" : msg) + "\n\n" + errorDetails;

            // NOTE: customErrorMessage != null alone shouldn't prevent error logging
            if (getStatus() != HttpServletResponse.SC_NOT_FOUND && !skipErrorLogging) {
                logError("ERROR at " + (isCommitted()? "(response committed) " : "") +
                        getRequestUrl() + "\n" + errorMsg);
            }

            if (isProductionMode()) {
                result = "<!doctype html><html><body style=\"margin:100px;\"><p style=\"font-size:62px; " +
                    "margin-bottom:100px;\">500<br>Server error</p><a href=\"" + homeUrl +
                    "\" style=\"margin-top: 200px;\">" + homeUrl + "</a></body></html>";
            }
            else {
                result = "<!doctype html><html><body><button style=\"width: 100%; height: 2.5em; " +
                    "background: yellow; font-family: monospace; font-weight: bold; border-width: 0;\" " +
                    "onclick=\"location.reload()\" autofocus>HIT ENTER TO REFRESH (" + getVersion() +
                    ")</button><pre>\n" + TemplateUtil.escapeHtml(errorMsg) + "\n</pre><a href=\"" + homeUrl +
                    "\" style=\"margin-top: 20px;\">" + homeUrl +
                    "</a></body><script>window.scrollTo(0,0);" +
                    (isDevelopmentMode()? getUpdateWatcherText() : "") + "</script></html>";
            }
        }
        catch (Throwable ee) {
            result = getStackTrace(ee);
        }

        return customErrorMessage != null? customErrorMessage.toString() :
            isAjaxRequest()? e.toString()/* make ajax response terse */ : result;
    }

    private ScriptableMap
        paramArrays,
        multipartUpload, // uploaded data
        stash,           // current request extra storage
        cookies,
        param;           // current request parameters

    private ScriptableMap templateParam = null;

    /**
     * Returns shared per request template parameter object
     */
    public ScriptableMap getTemplateParam() {

        if (templateParam == null) {
            templateParam = new ScriptableMap("Template Parameters", false, false);
            templateParam.put("r", this);
        }

        return templateParam;
    }

    NativeArray actionArray = null; // but also NativeArray

    public NativeArray getActionArray() {

        if (actionArray == null && originalActionPath != null) {
            ArrayList<String> aa = new ArrayList<>(8);

            for (int i = 1, j = 0; j != -1;) {
                j = originalActionPath.indexOf('/', i);

                if (j == -1)
                    aa.add(originalActionPath.substring(i));
                else {
                    aa.add(originalActionPath.substring(i, j));
                    i = j + 1;
                }
            }

            actionArray = (NativeArray)Context.getCurrentContext().newArray(getGlobalScope(), aa.toArray());
        }

        return actionArray;
    }

    /**
     * Return request parameter name to value map
     */
    public Scriptable getParam() {

        if (param == null) {
            param = new ScriptableMap(getActionArray(), false /* do not report undef read */);
            Enumeration<String> p = getParameterNames();

            while (p.hasMoreElements()) {
                String name = p.nextElement();
                param.put(name, getParameter(name));
            }
        }

        return param;
    }

    // Same as getRequestUrl but includes posted parameters
    // This is useful for logging
    String requestUrlWithPost = null;
    public String getRequestUrlWithPost() {

        if (requestUrlWithPost == null) {
            StringBuilder sb = new StringBuilder();
            Scriptable p = getParam();

            for (Object k: p.getIds()) {
                sb.append(k);
                sb.append('=');

                String v = (k != null? p.get(k.toString(), p) : null) + "";

                if (v.length() > 50) // skip too long post parameters (e.g. base64 file upload)
                    v = v.substring(0, 15) + "...";

                sb.append(v);
                sb.append('&');
            }

            requestUrlWithPost = getRequestPath() + (sb.length() > 1? "?" : "") +
                sb.substring(0, sb.length() > 0? sb.length()-1 : 0);
        }

        return requestUrlWithPost;
    }

    /**
     * Same as getParam, but maps parameter names to array of values
     */
    public Scriptable getParamArrays() {

        if (paramArrays == null) {
            paramArrays = new ScriptableMap(getActionArray(), false /* do not report undef read */);
            Enumeration<String> p = getParameterNames();

            while (p.hasMoreElements()) {
                String name = p.nextElement();
                paramArrays.put(name, getParameterValues(name));
            }
        }

        return paramArrays;
    }

    /**
     * Returns Parts of a multipart/form-data type request
     */
    public Scriptable getParts() throws IOException {

        if (multipartUpload == null) {
            multipartUpload = new ScriptableMap(getActionArray(), false /* do not report undef read */);

            if (getRequestContentType().startsWith("multipart/form-data")) {

                try {

                    for (Part part: getRequestParts()) {
                        multipartUpload.put(part.getName(), getRequestPart(part.getName()));
                    }
                }
                catch (ServletException e) {
                    throw new IOException(e);
                }
            }
        }

        return multipartUpload;
    }

    public static String getPartString(Part part) throws IOException {
        StringWriter s = new StringWriter();
        Files.copyStream(new InputStreamReader(part.getInputStream()), s);

        return s.toString();
    }

    public static byte[] getPartBytes(Part part) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Files.copyStream(part.getInputStream(), output);

        return output.toByteArray();
    }

    public void clearStash() {
        stash = null;
    }

    public ScriptableMap getStash() {

        if (stash == null) {
            stash = new ScriptableMap(false); // do not report undef read
        }

        return stash;
    }

    /**
     * Returns a javascript object with cookie names and values
     */
    public Scriptable getCookies() {

        if (cookies == null) {
            cookies = new ScriptableMap(false); // do not report undef read
            Cookie c[] = getRequestCookies();

            if (c != null) {
                cookies.put("$$cookies", cookies, c);

                for (int i = 0; i < c.length; i++) {
                    cookies.put(c[i].getName(), cookies, c[i].getValue());
                }
            }
        }

        return cookies;
    }

    // used to get unique object ID in javascript code
    public static String getId(Object v) {
        return v.toString();
    }

    public static boolean isScriptable(Object v) {
        return v instanceof Scriptable;
    }

    public final static class JobQueue {

        public final LinkedBlockingQueue<Callable> jobs;
        final Thread queueThread;

        public JobQueue(int capacity) {
            jobs = new LinkedBlockingQueue<Callable>(capacity);
            RhinoContextListener.registerJobQueue(this);

            queueThread = new Thread(()-> {

                while (true) {

                    try {
                        jobs.take().call();
                    }
                    catch (InterruptedException e) {
                        // let threadLocalPerTaskCleaner cleanup connections cached per DS per thread
                        //Jdbc.closeAll(); // close any cached jdbc connections on the current thread
                        threadLocalPerTaskCleaner.doCleanup();
                        return; // time to exit
                    }
                    catch (Throwable e) {
                        HttpRequest.logError(HttpRequest.getStackTrace(e));
                    }
                    finally {
                        HttpRequest.flushLoggers();
                    }
                }
            });
        }

        public void push(final Callable func) {

            if (!queueThread.isAlive())
                queueThread.start();

            jobs.offer(func);
        }

        public void push(final RhinoHttpRequest r, final Function func, final Object... param) {

            if (!queueThread.isAlive())
                queueThread.start();

            jobs.offer(()-> {
                runInJsEnv(r, func, param);

                return null;
            });
        }

        public void shutdown() {

            if (queueThread.isAlive())
                queueThread.interrupt();
        }
    }

    /**
     * Used to delay job execution until flushed
     */
    public final static class JobQueueBuffer {

        final List<Callable> jobs = new ArrayList<Callable>();
        final RhinoHttpRequest r;
        final JobQueue realJQ;

        public JobQueueBuffer(RhinoHttpRequest r, JobQueue realJQ) {
            this.r = r;
            this.realJQ = realJQ;
        }

        public void push(final Callable func) {
            jobs.add(func);
        }

        public void push(final Function func, final Object... param) {

            jobs.add(()-> {
                runInJsEnv(r, func, param);

                return null;
            });
        }

        public void flush() {

            for (Callable c: jobs)
                realJQ.push(c);
        }
    }

    /**
     * Used to run registered thread local cleanup procedures after each task/request
     */
    public final static class ThreadLocalPerTaskCleaner {

        final List<Runnable> cleaners = new ArrayList<Runnable>();

        public void register(final Runnable func) {
            cleaners.add(func);
        }

        protected void doCleanup() {

            for (Runnable r: cleaners) {

                try {
                    r.run();
                }
                catch(Throwable e) {
                    HttpRequest.logError(HttpRequest.getStackTrace(e));
                }
            }
        }
    }
}


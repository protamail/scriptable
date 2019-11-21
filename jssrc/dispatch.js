var conf = _r.config;

/*
 * Dispatch the current action
 * A handler must return null or undefined value to indicate no result
 * @param actionScope an object or array of objects to dispatch on
 * @return result of the handler if found, empty string otherwise
 */
function _dispatch(r, actionScope, templateParam, actionScopeStack) {

    if (actionScope == null)
        throw "_dispatch: actionScope can not be null";
    if (actionScopeStack.length > 100)
        throw "_dispatch: recursion too deep";

    var result, before, after;
    var actions = r.actionArray;
    var action = actions[0];
    var p = r.param;

    actionScopeStack.unshift(actionScope);

    // These will be processed right after request returns to the client
    if ("__postprocess__" in actionScope)
        r.afterRequestQueue.push(actionScope["__postprocess__"], r, p, templateParam);

    // NOTE: __before__ handler can skip normal processing by returning a value
    if (actionScopeStack[1] !== actionScope) { // skip __before__ if recursive
        if ("__before__" in actionScope)
            before = actionScope["__before__"](r, p, templateParam);
    }

    if (before == null) { // skip normal processing if __before__ returned value

        // try exact match first (actionScope isn't regular Object, i.e. no prototype, no builtin props)
        if (action in actionScope) {
            actions.shift(); // remove top action for exact match processing
            result = actionScope[action](r, p, templateParam);
            actions.unshift(action); // restore current action for the rest of the processing
        }

//        // if exact match didn't yield any result, try regexp match
//        for (var i; result == null && (i = doesRegexpMatch(actionScope, actionPath, i)) != -1; i++) {
//            var f = actionScope[actionScope["__regexp__"][i][1]];
//            result = f(r, p, templateParam);
//        }

        // if no result from either exact or regexp match, try  __default__ handler
        if (result == null) {
            if ("__default__" in actionScope) {
                result = actionScope["__default__"](r, p, templateParam);
            }
        }
    }

    // NOTE: __after__ handler can override normal result by returning a value
    if (actionScopeStack[1] !== actionScope) { // skip __after__ if recursive
        if ("__after__" in actionScope)
            after = actionScope["__after__"](r, p, templateParam, before != null? before : result);
    }

    actionScopeStack.shift();

    return result = before != null? before : after != null? after : result;
}

//function doesRegexpMatch(scope, actionPath, startIdx) {
//    if (!("__regexp__" in scope)) {
//        sync(function() { // synchronized on scope
//            if (!("__regexp__" in scope)) {
//                var re = [];
//                for (var i=0,k=Object.keys(scope); i<k.length; i++) { // iterate over own properties only
//                    var a = k[i];
//                    if (a.startsWith("/") && a.endsWith("/") && a.length > 1) {
//                        //(a.startsWith("^") || a.endsWith("$")) && a.length > 1)
//                        re.push([ new RegExp(a.substring(1, a.length-1)), a ]);
//                    }
//                }
//                scope["__regexp__"] = re;
//            }
//        }, scope)();
//    }
//
//    if (startIdx == null)
//        startIdx = 0;
//
//    var re = scope["__regexp__"];
//
//    for (var i=startIdx; i<re.length; i++) {
//        if (re[i][0].exec(actionPath) != null)
//            return i;
//    }
//
//    return -1;
//}

function error_handler(r, e) {
    var actionScopeStack = r.actionScopeStack;

    for (var i = 0; i < actionScopeStack.length; i++) {
        var actionScope = actionScopeStack[i];

        if ("__error__" in actionScope) {
            var error = actionScope["__error__"](r, r.param, r.templateParam, e);

            if (error != null)
                return error;
        }
    }

    return null;
};

/**
 * Entry point of an HTTP request
 */
var lastReloadTime = 0;
var rootDispatchObject = null; // postpone loading the rest of the app

_r.startFunc = function(r) {

    if (__developmentMode__) {
        var millis = Date.now();

        if ((millis - lastReloadTime) > 2000) { // don't try to reload more often than every 2 sec

            if ("before-js-reload-targets" in conf) {
                // this way we don't rely on user defined __beforeJsReload__ handler,
                // and therefore, don't need to load rootDispatchObject before doing this
                _r.runGenericTranspileTask(conf["before-js-reload-targets"]);
            }
            else if (__developmentMode__) {
                logError("Missing essential development environment configuration: before-js-reload-targets needs to be defined in scriptable.properties so that updated server scripts can be recompiled (according to the specified targets) and reloaded.");
            }

            _r.reloadUpdatedModules();
            lastReloadTime = millis;
        }
    }
    r.setRequestTimerCheckpoint("dispatch");

    var actionScopeStack = [];
    var templateParam = r.templateParam;

    r.errorHandler = error_handler;
    r.actionScopeStack = actionScopeStack;
    r.dispatchOn = function (actionObj) {
        return _dispatch(r, actionObj, templateParam, actionScopeStack);
    }

    if (rootDispatchObject == null) { // make __beforeJsReload__ handler available
        rootDispatchObject = require(__rootDispatchFile__);
    }

    var result = _dispatch(r, rootDispatchObject, templateParam, actionScopeStack);

//    if (r.status >= 400) sometimes we need to return custom content along with the explicit error code
//        throw "Error status: " + r.status;

    if (result == null) {
        actionScopeStack.unshift(rootDispatchObject); // provide action scope for the JS error_handler
        throw _r.notFound();
    }

    return result;
}

/**
 * Recognized sendContent parameter properties, mapped to their canonical name
 */
var sendContentProps = {
    type: "type",
    data: "data",
    sendData: "data",
    file: "file",
    sendFile: "file",
    lastModified: "lastModified",
    lastModifiedMillis: "lastModified",
    expireSec: "expireSec",
    expiresSec: "expireSec",
    contentType: "contentType",
    binary: "binary",
    contentCallback: "contentCallback"
};

/**
 * @param res object with properties specified in sendContentProps above
 */
exports.sendContent = function(res) {
    var r = this instanceof _r? this : _r.r;

    if (res == null)
        return;

    for (var k in res) {
        var key = sendContentProps[k];
        if (key == null)
            throw "sendContent: property '" + k + "' not supported.";
        else
            res[key] = res[k];
    }

    if (!("sendContent_allowed_file_types" in conf))
        throw "sendContent: missing required configuration: sendContent_allowed_file_types.";
    var allowedTypes = conf.sendContent_allowed_file_types;
    var lastModified = res.lastModified;
    var type = res.type || (res.file != null && Files.getFileExtension(res.file));
    var defaultExpireSec = res.expireSec || 30*24*60*60; // 30 days

    if (res.file != null) {
        lastModified = Files.getFile(res.file).isFile()? Files.getLastModified(res.file) : 0;
    }
    else if (!lastModified) {
        lastModified = r.requestTime;
        defaultExpireSec = res.expireSec || 0; // dynamic data, default to no caching
    }

    if (!lastModified)
        throw _r.notFound(); // if still lastModified == 0 -- could be result of missing/unreadable file

    // explicitly round to closest second since If-Modified-Since is at a second granularity
    lastModified = Math.round(lastModified/1000.)*1000;

    if (lastModified == r.getDateHeader("If-Modified-Since")) {
        return exports.notModified();
    }

    // OK, now that we do need to retrieve the data to send
    var data = typeof res.contentCallback == "function"? res.contentCallback() : res.data;
    if ((!type || type == "json") && data != null && typeof data == "object") {
        type = "json";
        data = exports.toJSON(data);
    }

    type = type || "html"; // default type to HTML

    r.setHeader("Cache-Control", "max-age=" + defaultExpireSec);
    r.setDateHeader("Last-Modified", lastModified);
    r.setDateHeader("Expires", r.requestTime + defaultExpireSec*1000);
    var binary = false;

    if (allowedTypes[type] != null) {
        r.setContentType(allowedTypes[type].contentType || "text/plain");
        binary = allowedTypes[type].binary;
        if (allowedTypes[type].expiresSec != null && !("expireSec" in res)) {
            r.setHeader("Cache-Control", "max-age=" + allowedTypes[type].expiresSec);
            r.setDateHeader("Expires", r.requestTime + allowedTypes[type].expiresSec*1000);
        }
    }
    else {
        if (data == null)
            throw type + " type is not specified in sendContent_allowed_file_types config object.";
        else
            r.setContentType("text/plain"); // set default mime type for database stored data
    }

    // override with res values, if passed
    if (res.contentType != null)
        r.setContentType(res.contentType);

    if (res.binary != null)
        binary = res.binary;

    try { 
        r.status = 200;
        if (data == null) {
            binary? r.sendBinaryFile(res.file) : r.sendTextFile(res.file);
        }
        else {
            binary? binary === "base64"? r.sendBase64Blob(data) : r.sendBlob(data) : r.sendString(data);
        }
    } 
    catch (e) { 
        logError("sendContent: " + see(e)); 
        if (__developmentMode__) {
            r.status = 500;
            r.sendString(see(e));
        }
        else
            throw _r.notFound(); 
    } 
    return ""; 
} 

exports.sendJson = function(data) {

    if (typeof(data) !== "string")
        data = exports.toJSON(data);

    return exports.sendContent({
        data: data,
        type: "json",
        expireSec: 0
    });
}



exports.remoteEval = function() {
    var r = this instanceof _r? this : _r.r;
    var n = r.stash.bk_eval_idx || 1;

    if (n == 1) // don't overwrite already existing eval cookies (didn't execute because of redirect)
        for (; r.cookies['bk_eval'+n] != null; n++) {
        }

    if (n > 10) { // too many eval cookies (client is likely not evaluating them)
        for (n--; n>0; n--)
            exports.clearCookie('bk_eval'+n);
        return;
    }

    if (arguments.length == 1) {
        exports.setUECookie('bk_eval'+n, exports.toJSON(arguments[0]));
    }
    else {
        var args = [];
        for (var i=0,l=arguments.length; i<l; i++)
            args.push(arguments[i]);
        exports.setUECookie('bk_eval'+n, exports.toJSON(args));
    }
    r.stash.bk_eval_idx = n+1;
}

exports.remoteLog = function(msg) {
    if (exports.isArrayLike(msg))
        msg = exports.flatten(msg); // convert to js array

    if (msg != null && typeof msg == "object")
        msg = exports.toJSON(msg);
    exports.remoteEval("console.log", "remoteLog: " + msg);
};

/**
 * Send redirect back to the client
 * @param location path
 */
exports.sendRedirect = function(requestUrl) {
    var r = this instanceof _r? this : _r.r;

    if (r.ajaxRequest) {
        // do not try to redirect ajax requests,
        // just cause page reload to trigger another redirect etc
        return exports.notAuthorized();
    }
    else if (!r.isCommitted()) {
        r.sendRedirectResponse(requestUrl.match(/^http.?:\/\//)? requestUrl :
            requestUrl == null || requestUrl == ""? (r.hostUrl + r.contextPath) :
            (r.hostUrl + r.contextPath + (requestUrl.startsWith("/")? "" :
                r.originalActionPath.substring(0, r.originalActionPath.lastIndexOf("/")+1)) + requestUrl));
    }
    return "";
}

/**
 * Returning or throwing result of this function in an action handler
 * will result in status 404 and "page not found" message returned to the client
 */
exports.notFound = function() {
    _r.r.status = 404;
    throw "404 - NOT_FOUND"; // make sure __error__ handler is called
}

/**
 * Returning or throwing result of this function in an action handler
 * will result in message being sent back to client along with error status code of 515
 */
exports.validationError = function(message) {
    _r.r.sendValidationError(message);
    throw "VALIDATION ERROR"; // make sure __error__ handler is called
}

exports.errorPopup = function(msg) { // return error page to client with status: 555 and content: msg
    _r.r.status = 555;
    _r.r.setAttribute("error_message_555", msg);
    throw "error 555";
}

exports.notModified = function() {
    var r = this instanceof _r? this : _r.r;
    r.status = 304;
    return "";
}

exports.notAuthorized = function() {
    _r.r.status = 401;
    throw "401 - NOT_AUTHORIZED"; // make sure __error__ handler is called
}

exports.notOK = function(statusCode) {
    if (statusCode > 400) {
        _r.r.status = statusCode;
        throw statusCode + " - NOT_OK"; // make sure __error__ handler is called
    }
}

exports.OK = ""; // must be empty string, to not be sent in response

/**
 * Send and retrieve URI-encoded cookie values
 */
exports.setUECookie = function(name, value, expireSec) {
    _r.r.setPlainCookie(name, escape(value), expireSec || 0);
}

exports.getUECookie = function(name) {
    var c = _r.r.getPlainCookieValue(name);
    return c == null? c : unescape(c);
}

exports.clearCookie = function(name) {
    _r.r.clearCookie(name);
}

exports.isAjax = function() {
    return _r.r.ajaxRequest;
}


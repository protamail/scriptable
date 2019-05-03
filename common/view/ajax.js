// options object: method, async, requestHeaders, onComplete
function getXhr(options) {
    let xhrObj = new XMLHttpRequest();
    getXhr.activeRequests.push(xhrObj);

    if (options.method == null)
        options.method = 'post';

    options.async = options.async == null || options.async;
    options.method = options.method.toLowerCase();

    xhrObj.onreadystatechange = function() {
        let readyState = xhrObj.readyState;

        if (readyState == 4) { // on complete

            if (!getXhr.removeFromActive(xhrObj)) {
                console.log(xhrObj + ": cancelled");
                return; // request has been aborted
            }

            if (xhrObj.status == 401)  // UNAUTHORIZED
                location.reload();       // reload, so the page can be redirected to the login screen
            else if (options.onComplete != null)
                options.onComplete();
        }
    };

    return {
        options: options,
        isSuccess: function() {
            let code = xhrObj.status;

            return code == null || code == 0 || code >= 200 && code < 300;
        },
        getStatus: function() {
            return xhrObj.status;
        },
        getStatusText: function() {
            return xhrObj.statusText;
        },
        getResponseText: function() {
            return xhrObj.responseText;
        },
        getResponseHeader: function(name) {
            return xhrObj.getResponseHeader(name);
        },

        send: function(url, postBody) {

            // readyState: 0 - not_opened, 1 - not_sent, 2 - awaiting_response, 3 - receiving_data,
            // 4 - request_complete
            if (xhrObj.readyState > 0 && xhrObj.readyState < 4) // is still processing previous request?
                return;

            // must open before doing anything else to the xhrObj
            if (options.method == 'get' && postBody) {
                url += (url.indexOf('?') == -1 ? '?' : '&') + postBody;
                postBody = null;
            }

            xhrObj.open(options.method, url, options.async);

            let requestHeaders = {
                "X-Requested-With": "XMLHttpRequest",
                "Accept": "text/javascript, text/html, application/xml, text/xml, */*"
            };

            if (options.requestHeaders)
                extendObject(requestHeaders, options.requestHeaders);

            for (let k in requestHeaders)
                xhrObj.setRequestHeader(k, requestHeaders[k]);

            xhrObj.send(postBody);
        }
    };
}

getXhr.activeRequests = [];

getXhr.removeFromActive = function(xhr) {
    let i = getXhr.activeRequests.indexOf(xhr);

    if (i != -1) {
        getXhr.activeRequests.splice(i, 1);

        return xhr;
    }
}

//$(document).ready(evalRemoteCallCookies);

function globalEval(data) {

    if (data != null) {
        return (function (data) {
            return eval.call(window, data);
        })(data);
    }
}

function evalScripts(target) {
    let scripts = target.getElementsByTagName('SCRIPT');

    for (let i=0, l=scripts.length; i<l; i++)
        globalEval(scripts[i].innerHTML);
}

function getContextPath() {
    let ctx = location.pathname;
    let i = ctx.indexOf("/", 1);

    if (i > 0)
        ctx = ctx.substring(0, i);

    return ctx;
}

function clearCookie(name) {
    document.cookie = name + '=; path=' + getContextPath() +
        '; expires=' + new Date(Date.now() - 1*1000*3600*24).toUTCString();
}

function getCookie(name) {
    let c = document.cookie, nameq = name + "=";
    let i = c.indexOf("; " + nameq);
    i = i > 0? i + 2 + nameq.length : -1;

    if (i == -1)
        i = c.indexOf(nameq) == 0? nameq.length : -1;

    let j = c.indexOf("; ", i + 1);

    if (i > 0)
        return unescape(c.substring(i, j > 0? j : c.length));
}

function setCookie(name, value) {
    document.cookie = name + '=' + escape(value) + '; path=' + getContextPath() +
        '; expires=' + new Date(Date.now() + 3650*1000*3600*24).toUTCString();
}

function setSessionCookie(name, value) {
    document.cookie = name + '=' + escape(value) + '; path=' + getContextPath();
}

function evalRemoteCallCookies() {
    let c = document.cookie.split('; ');

    for (let i=0; i<c.length; i++) {

        if (c[i].indexOf("bk_eval") == 0) {
            let j = c[i].indexOf("=");
            let val = unescape(c[i].substring(j + 1));
            clearCookie(c[i].substring(0, j));

            try {

                if (val.indexOf('"') == 0)
                    val = globalEval(val); // remove double quoting

                if (val != null)
                    evalCode(val);
            }
            catch (e) {
                console.log("eval(" + val + ") error: " + e);
            }
        }
    }
}

function evalCode(code) {

    if (typeof code === "string")
        code = globalEval(code);

    if (typeof code === "function")
        code();
    else if (code instanceof Array) { // check if this can be interpreted as a function call
        let func = globalEval(code.shift());

        if (typeof func === 'function')
            func.apply(window, code);
    }
}

function refreshPage() {
    location.href = location.pathname + location.search; // exclude # portion if any
}

// decode url-encoded string into object
function decodeUrlData(url) {

    if (typeof (url) == "object")
        return url;

    let result = {};

    if (url[0] == '?')
        url = url.substring(1);

    let qe = url.indexOf('#');

    if (qe != -1)
        url = url.substring(0, qe);

    url = url.split(/&/);

    for (let i=0, l=url.length; i<l; i++) {
        let param = url[i].split('=');
        if (param[0])
            result[param[0]] = param[1] == null? true : decodeURIComponent(param[1]);
    }

    return result;
}

function encodeUrlData(data) {

    if (typeof (data) == "string")
        return data;

    let result = '';

    for (let p in data) {

        if (data[p] != null)
            result += '&' + p + (data[p] === true? '' : ('=' + encodeURIComponent(data[p])));
    }

    return result.length? result.substring(1) : '';
}

function extendObject(o1, o2) {

    for (let k in o2) {

        if (o2[k] == "")
            delete o1[k]; // if x is of the form ?x=&y=1 then assume x should be removed
        else
            o1[k] = o2[k];
    }

    return o1;
}

// augment path with data params if path already includes search clause
// otherwise, augment current location with provided path and data params
function extendUrl(path, data, truncate = undefined) {

    if (path != null && typeof path === "object") {
        data = path;
        path = null;
    }

    let url = path;
    let i = -1;

    if (url == null || (i = url.indexOf('?')) == -1) {
        url = location.href;
        i = url.indexOf('?');

        if (path == null || path == "") {
            path = url;

            if (i != -1)
                path = url.substring(0, i);
        }
    }

    let curData;

    if (i != -1) {
        curData = decodeUrlData(url.substring(i+1));

        if (truncate) { // delete all keys following the last one from data
            let curKeys = Object.keys(curData);
            let i = Math.max.apply(Math,
                                   Object.keys(data).map(function(x) { return curKeys.indexOf(x) }));

            if (i >= 0) {

                for (i++; i < curKeys.length; i++)
                    delete curData[curKeys[i]];
            }
        }
    }
    else
        curData = {};

    data = extendObject(curData, data); // even if curData empty, this step will drop any zzz=& props from data

    let search = encodeUrlData(data);

    return path + (search === ""? "" : "?") + search;
}

// postParams can include special properties: __async__, __updateElement__, __data__, __method__
function ajax(url, postParams) {

    if (url != null && typeof url === "object") {
        postParams = url;
        url = null;
    }

    if (postParams.__async || postParams.__update || postParams.__data || postParams.__method) {
        alert("__async, __update, __data, __method are deprecated, use __async__, __updateElement__, __data__, __method__ instead");

        return;
    }

    let async = postParams.__async__ == null || postParams.__async__;
    let update = postParams.__updateElement__;
    let data = postParams.__data__;
    let method = postParams.__method__ || "post";
    let onSuccess = postParams.__success__;
    let onError = postParams.__error__;
    let onComplete = postParams.__finally__;
    delete postParams.__async__;
    delete postParams.__updateElement__;
    delete postParams.__data__;
    delete postParams.__method__;
    delete postParams.__success__;
    delete postParams.__error__;
    delete postParams.__finally__;
    let requestHeaders = {};

    if (data == null)
        requestHeaders["Content-type"] = "application/x-www-form-urlencoded";

    let xhr = getXhr({
        async: async,
        method: method,
        onComplete: complete,
        requestHeaders: requestHeaders
    });

    function complete() {
        let text = xhr.getResponseText();
        let statusCode = xhr.getStatus();

        if (xhr.isSuccess()) {

            if (update != null && statusCode != 204 /* no content */) {
                let target = typeof (update) == "string"?
                    document.getElementById(update) : update;

                if (target) {
                    target.innerHTML = text;

                    // synchronously eval scripts, so onSuccess runs afterwards
                    evalScripts(target);
                }

                $(document).trigger("ajax-update-element", xhr);
            }

//            evalRemoteCallCookies();

            if (typeof onSuccess === 'function')
                onSuccess.call(xhr, text); // don't call these async, since request could be sync
        }
        else {
            let error = text || xhr.getStatusText();

            if (typeof onError == 'function')
                onError.call(xhr, error);
            else if (typeof ajax.__error__ == 'function')
                ajax.__error__(statusCode, error);
            else
                console.log("Error: " + error);
            
//            evalRemoteCallCookies();
        }
        if (typeof onComplete == "function")
            onComplete.call(xhr);
    };

    $(document).trigger("ajax-before-send", xhr);

    // if url is empty -- use the current location instead
    if (url == null || url == "")
        url = location.pathname + location.search;

    xhr.send(url, data != null? data : encodeUrlData(postParams));
}


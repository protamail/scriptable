exports.toJSON = scriptable.util.Json.toJSON;
exports.nop = exports.NOP = function() {};

exports.getTemplateScope = function() {
    return _r.r.templateParam;
};

/**
 * Define name space object chain. Existing objects will not be redefined.
 * @param {String} namespace Name space spec e.g. 'a.b.c' will define var a = {b:{c:{}}}
 */
exports.defns = function(namespace) {
    var comp = namespace.split(".");
    var scope = globalScope;
    for (var i=0, l=comp.length; i<l; i++) {
        if (!(comp[i] in scope))
            scope[comp[i]] = ScriptableMap();
        scope = scope[comp[i]];
    }
}

exports.getSoyFileCompiledSrc = function(r, file) {
    r.contentType = "text/plain";
    return getSoyCompiledText(file);
}

/**
 * Execute external command
 * @param cmd an array of tokenized command
 * @parma env array of environment variables
 * @param workDir new process working directory
 */
exports.exec = function(cmd, env, workDir) {
    var process = java.lang.Runtime.getRuntime().exec(cmd, env, new java.io.File(workDir));
    return process.waitFor();
}

exports.escapeJs = com.bkmks.JavaMethodHandles.escapeJs;
exports.escapeHtml = com.bkmks.JavaMethodHandles.escapeHtml;
exports.escapeUri = encodeURIComponent;

exports.augmentFirst = function(o1, o2, on) {
    for (var i=1, l=arguments.length; i<l; i++) {
        for (var k in arguments[i]) {
            o1[k] = arguments[i][k];
        }
    }
    return o1;
};

exports.toArray = function (arrayLike) {
    var array = [];
    for (var i=0,l=arrayLike.length; i<l; i++)
        array.push(arrayLike[i]);
    return array;
}

exports.lazyEval = function (paramObjAndNames, f, funcParams) {
    if (!(f instanceof Function))
        throw "lazyEval: the second parameter must be a function";
    if (!f.RECURSION_SAFE) {
        f = bindWithRecursionGuard.apply(this, arguments);
    }

    var param = paramObjAndNames[0];
    if (!(param instanceof exports))
        throw "lazyEval: feature supported on the instances of ScriptableMap only";
    if (!("__lazyProperties__" in param))
        param.__lazyProperties__ = {};

    var lp = param.__lazyProperties__;
    for (var i=1,l=paramObjAndNames.length; i<l; i++) {
        var name = paramObjAndNames[i];
        if (name in param || name in param.__lazyProperties__) // make sure lazy property doesn't exist yet
            throw "lazyEval: property already exists: " + name;
        lp[name] = f;
    }

    if (!("__noSuchProperty__" in param)) {
        param.__noSuchProperty__ = function (name) {
            var f = lp[name];
            if (f)
                f();
            else
                return; // indicate this property is not lazy
            return param[name];
        }
    }
}

exports.asyncEval = function (paramObjAndNames, f, funcParams) {
    return exports.asyncEvalWithExecutorService.apply(this, exports.makeArray(null, arguments));
}

exports.asyncEvalWithExecutorService = function (executorService, paramObjAndNames, f, funcParams) {
    if (!(f instanceof Function)) {
        throw "asyncEval: the second parameter must be a function";
    }

    var completion;
    var cf = function() {
        completion.await();
    }
    cf.RECURSION_SAFE = true;
    exports.lazyEval(paramObjAndNames, cf);

    // start task after lazy property has been setup to make sure the setup routine doesn't find it already existing
    var args = exports.makeArray(arguments);
    args.shift();
    args[0] = executorService;
    var r = _r.r;
    completion = r.callJsFunctionAsync.apply(r, args);
}

exports.backgroundJob = function (f, funcParams) {
    var r = _r.r;
    return r.callJsFunctionAsync.apply(r, exports.makeArray(null, arguments));
}

/**
 * Defines an action handler wrapper. This is used to avoid saving reference to an action function
 * in an action object property thereby preventing module reload to take effect
 */
exports.defAction = function(module, func) {
    var m = require(module);
    return function(r, p, t, z) {
        return m[func](r, p, t, z);
    }
}

exports.asyncReEval = function (paramObjAndNames, f, funcParams) {
    var param = paramObjAndNames[0];
    for (var i=1,l=paramObjAndNames.length; i<l; i++) {
        delete param[paramObjAndNames[i]];
    }
    exports.asyncEval.apply(this, arguments);
}

exports.sleep = function (millis) {
    _r.r.sleep(millis);
}

function bindWithRecursionGuard(paramObjAndNames, f, funcParams) {
    var args = []; // args must be copied here for closure use
    for (var i=2,l=arguments.length; i<l; i++)
        args.push(arguments[i]);
    var recursionDetected = false;
    return function () {
        if (recursionDetected)
            throw "Detected recursive loop in: " + f;
        recursionDetected = true;
        var result = f.apply(this, args);
        recursionDetected = false;
        return result;
    };
}

exports.isArrayLike = function(arr) {
    var length = arr != null && typeof arr == "object" && "length" in arr? arr.length : null;
    return length > 0 && (length-1) in arr || length === 0;
}

/**
 * Concatenate object parameters and elements of array-like parameters into an array
 */
exports.makeArray = function(a1, a2, an) {
    var arr = [];
    for (var i=0, l=arguments.length; i<l; i++) {
        if (!exports.isArrayLike(arguments[i]))
            arr.push(arguments[i]);
        else
            for (var j=0, k=arguments[i].length; j<k; j++)
                arr.push(arguments[i][j]);
    }
    return arr;
}

exports.flatten = function(a1, a2, an) {
    var arr = [];
    for (var i=0, l=arguments.length; i<l; i++) {
        if (!exports.isArrayLike(arguments[i]))
            arr.push(arguments[i]);
        else
            for (var j=0, k=arguments[i].length; j<k; j++)
                arr.push(arguments[i][j]);
    }
    return arr;
}


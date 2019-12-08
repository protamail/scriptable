/**
 * This module is used to initialize global functions and variables
 * It is not dependant on any other modules, it's loaded by runtime environment and never require'd
 */

if ("require" in this)
    throw "_init.js shouldn't be reloaded"; // never redefine _init.js (in the existing global context)

/**
 * 1. All global variables must be defined in this module since top scope will be sealed after loading it
 * 2. No variables can be exported other than via globalScope since this module can't be require'd
 */
this.globalScope = this.__globalScope__ = this.global = this;
this.scriptable = Packages.org.scriptable;

var JavaMethodHandles = scriptable.JavaMethodHandles;

__globalScope__.empty = new this.scriptable.EmptyThing(); // for use as default value in deep deconstructing
__globalScope__.empty[Symbol.iterator] = function () {    // make it iterable (for array destructuring)
    return { next: function () { return { value: __globalScope__.empty, done: false } } };
}

__globalScope__._r = scriptable.ScriptableRequest._r;
__globalScope__.log = _r.log;
__globalScope__.logInfo = _r.logInfo;
__globalScope__.logEvent = _r.logEvent;
__globalScope__.logError = _r.logError;
__globalScope__.see = scriptable.util.Json.describe;
__globalScope__.seeHtml = function (what) {
    return "<http><body><pre>" + see(what) + "</pre></body></html>";
}

__globalScope__.__developmentMode__ = _r.isDevelopmentMode() ? true : false;
__globalScope__.__testEnvironment__ = _r.isTestEnvironment() ? true : false; // production mode in test env
__globalScope__.__productionMode__ = !__developmentMode__; // anything other than development
__globalScope__.__compilationMode__ = _r.isCompilationMode()? true : false; // anything other than development

__globalScope__.Strings = scriptable.util.Strings;
__globalScope__.Base64 = scriptable.util.Base64;
__globalScope__.Files = scriptable.util.Files;
__globalScope__.Resources = scriptable.util.Resources;
__globalScope__.ScriptableMap = scriptable.ScriptableMap;
__globalScope__.Http = scriptable.util.Http;
// __globalScope__.Emailer = scriptable.util.Emailer; don't require mail.jar to be present unless needed
__globalScope__.LruCache = scriptable.LruCache;
__globalScope__.DataSourceConfig = scriptable.util.JdbcDataSourceConfig;
__globalScope__.Jdbc = scriptable.util.Jdbc;
__globalScope__.JavaUtil = scriptable.util.JavaUtil;
__globalScope__.ValidationError = scriptable.ValidationException;

__globalScope__.console = {

    log: function() {
        for (var i=0; i<arguments.length; i++)
            _r.log(see(arguments[i]));
    }
}

// create some ES2017 global variables to avoid undef read issues in generated code
//__globalScope__.Symbol = null;

/**
 * Returns a function synchronized on obj or func itself
 */
__globalScope__.sync = function(func, obj) {
    return new Packages.org.mozilla.javascript.Synchronizer(func, obj || func);
};

//
// construct app version string based on build.number properties files
//
var pf = Files().class.classLoader.getResources("build.number");
var versionList = [];
_r.version = "";

while (pf.hasMoreElements())
    versionList.push(pf.nextElement());

versionList.reverse();
var versionString = [];

for (var i in versionList) {
    var version = Files.loadPropertiesFromStream(versionList[i].openStream());
    versionString.push("build.number" in version? version["build.number"] : "?");
}

_r.version = versionString.join(", ");

/**
 * Traverse loaded modules list and reload updated ones
 */
var reloadUpdatedModules = sync(function () {
    /**
     * Since 'require' doesn't care if file has been updated, file groups (defined by property base name)
     * need to be reloaded _after_ regular module files. This way we'll get up-to-date module function
     * to mixin its exports into group's exports
     */
    for (var name in INC) {
        if ("file" in getModUpdateStatus(name, INC[name].lastModified)) {
            INC[name].lastModified = 0; // force module reload
            require(name);
        }
    }
}, INC);

// sync to eliminate race conditions when checking key presence in cachedSourceDirs
var listSourceFilesCached = function(baseProp) {
    return Resources.listSourceFiles(listSourceDirsCached(baseProp), _r.config, baseProp);
}

var cachedSourceDirs = {};
var listSourceDirsCached = sync(function(baseProp) {
    if (!(baseProp in cachedSourceDirs))
        // we always cache directories where matched files were found to significantly improve
        // file listing overhead while still tracking file additions/deletions
        cachedSourceDirs[baseProp] = Resources.listSourceDirs(_r.config, baseProp);

    return cachedSourceDirs[baseProp];
}, cachedSourceDirs);

// called from functions synchronized on INC
function getModUpdateStatus(file, lastModified) {

    if (lastModified == -2) // currently loading, return
        return { lastModified: lastModified };

    if (lastModified != 404) { // if this file wasn't loaded from bytecode
        var transFile = _r.TRANSPILE_DIR + file + ".js";

        var t = Files.getLastModified(transFile);

        if (t != 0) {
            
            if (t > lastModified)
                return { lastModified: t, file: file, fileName: transFile };
            else
                return { lastModified: lastModified };
        }

        // transpiled version doesn't exist
        t = Files.getLastModified(file);

        if (t == 0) // file not found
            delete INC[file]; // prevent persistent reloading of a missing file
        else if (t > lastModified)
            return { lastModified: t, file: file, fileName: file };
    }

    return { lastModified: lastModified };
}

__globalScope__.Htm = JavaMethodHandles.applyJsTemplate;
// HtmlFragment object is a marker indicating content is already HTML safe,
// it is recognized by Htm/applyJsTemplate above, as well as MD interpolator
Htm.html = JavaMethodHandles.htmlFragment;
Htm.js = JavaMethodHandles.htmlFragment;
Htm.id = JavaMethodHandles.htmlFragment;
Htm.noescape = JavaMethodHandles.htmlFragment;
Htm.escapeJs = JavaMethodHandles.escapeJs;
Htm.unescapeUnicode = JavaMethodHandles.unescapeUnicode;
Htm.escapeB64 = JavaMethodHandles.escapeB64;
Htm.escapeUri = encodeURIComponent;
Htm.escapeHtml = JavaMethodHandles.escapeHtml;
Htm.unescapeHtml = JavaMethodHandles.unescapeHtml;
Htm.format = JavaMethodHandles.formatAny;

Object.defineProperty(Htm, 'keepWs', {
    set: function(v) {
        org.scriptable.template.TemplateUtil.JS_TEMPLATE_STRIP_WS = !v;
    }
});

/**
 * Override some soy templates functions with faster java equivalents
 */
__globalScope__.soy = new ScriptableMap("Soy global object");
soy.$$escapeJsString = JavaMethodHandles.escapeJs;
soy.$$escapeJsValue = JavaMethodHandles.escapeJs;
soy.$$unescapeUnicode = JavaMethodHandles.unescapeUnicode;
// not common
soy.$$filterCssValue = function (v) { return v };
soy.$$filterNormalizeUri = function (v) { return v }; // normalize path before escaping URI
soy.$$filterHtmlAttributes = function (v) { return v };

soy.$$escapeHtml = JavaMethodHandles.escapeHtml;
soy.$$escapeHtmlAttribute = JavaMethodHandles.escapeHtml;
soy.$$escapeHtmlRcdata = JavaMethodHandles.escapeHtml;
soy.$$escapeHtmlAttributeNospace = JavaMethodHandles.escapeHtml; // not quoted attr

soy.$$format = JavaMethodHandles.format;
soy.$$formatAny = JavaMethodHandles.formatAny;
soy.$$formatDate = JavaMethodHandles.formatDate;
soy.$$escapeUri = encodeURIComponent;
soy.$$escapeB64 = JavaMethodHandles.escapeB64;
// used by keys() function, returns group keys in the order they were added.
// Rhino preserves insertion order, so Object.keys(obj) could be used instead
soy.$$getMapKeys = Object.keys;

__globalScope__.goog = {}; // to satisfy referenses to goog.DEBUG, etc.

/**
 * Builds an augmented data object to be passed when a template calls another,
 * and needs to pass both original data and additional params. The returned
 * object will contain both the original data and the additional params. If the
 * same key appears in both, then the value from the additional params will be
 * visible, while the value from the original data will be hidden. The original
 * data object will be used, but not modified.
 *
 * @param {!Object} origData The original data to pass.
 * @param {Object} additionalParams The additional params to pass.
 * @return {Object} An augmented data object containing both the original data
 *     and the additional params.
 */
soy.$$augmentMap = function(origData, additionalParams) {

    // Create a new object whose '__proto__' field is set to origData.
    /** @constructor */
    function tempCtor() {};
    tempCtor.prototype = origData;
    var newData = new tempCtor();

    // Add the additional params to the new object.
    for (var key in additionalParams) {
        newData[key] = additionalParams[key];
    }

    return newData;
};

/*
 * Must be global, since used by some external libs, e.g. less compiler
 */
__globalScope__.readFile = function(file) { // used by less compiler
    return Files.getFileAsString(file);
};

/**
 * Loaded module registry indexed by module/path/name.xx
 * Need to define this before the first require invokation
 */
var INC = new ScriptableMap(false);

/**
 * Copy own exported properties
 * @param {Object} from
 * @param {Object} to
 */
function extend(to, from, noRedefine) {
    for (var k=Object.keys(from),i=0,l=k.length; i<l; i++) { // copy own properties only
        var p = k[i];
        if (!noRedefine)
            delete to[p]; // ignore possible redefine error
        to[p] = from[p];
    }
}

/**
 * This is called when special exports variable is invoked as a function with module file name as a parameter
 * e.g. exports("/module/path/name.js");
 */
function exportsFunc(file, paramObj) {
    return require(file, true, paramObj);
}

/**
 * NOTE: This module must not require any other modules, so define this function last
 *
 * Load a javascript module if it hasn't been loaded already.
 * Will reload if the loaded file has been modified, but only when in development mode
 * @param {String} file Name specified relative to the content base directory
 * @param {boolean} isBeingExported Indicates if this file is being require'd by means of exports(file);
 */
__globalScope__.require = sync(function(file, isBeingExported, paramObj) { // synchronized on global scope

    if (file.charAt(0) != "/") { // this is relative path
        if (__contextPath__ == null) {
            throw "Relative path require('" + file + "') can only be used at module's top level.";
        }
        file = __contextPath__ + file;
    }

    // assume .js extension
    //if (!file.endsWith(".js"))
    //    file += ".js";

    file = Files.normalizePath("/" + file);

    var newEntry = (INC[file] == null);

    // skip the loading in prod if already loaded or if just loaded in dev
    if (newEntry || !INC[file].lastModified || (__developmentMode__ &&
                (!INC[file].lastChecked || (Date.now() - INC[file].lastChecked) > 1000))) {

        var updated = getModUpdateStatus(file, newEntry? 0 : INC[file].lastModified);
        newEntry = (INC[file] == null); // might have been deleted in the previous step (getModUpdateStatus)

        if (newEntry && !('file' in updated)) {
            updated = {
                file: file,
                fileName: file,
                lastModified: 404 // try loading from bytecode
            };
        }

        if ('file' in updated) {
            try {
                // Note: recursive calls to require are synchronized on the global scope, see _r.evaluateFile
                // Keep old exports object on reload so saved references to it still work
                var exportsObj = INC[file] != null? INC[file].exports :
                    // allow exports be callable, no undef read, no redefine
                    new ScriptableMap(exportsFunc, "exports object of " + file, true, true);

                for (var k=Object.keys(exportsObj),i=0; i<k.length; i++) {
                    delete exportsObj[k[i]]; // clear own properties of exports before reloading
                }

                INC[file] = {

                    lastModified: -2, // mark module as being loaded to allow mutually recursive requires
                    exports: exportsObj,
                    moduleFunc: null
                };

                INC[file].moduleFunc = _r.evaluateFile(updated.fileName,
                        updated.file.substring(0, updated.file.lastIndexOf("/") + 1), exportsObj);
                INC[file].lastModified = updated.lastModified;
                INC[file].lastChecked = Date.now();
            }
            finally {
                if (INC[file] && INC[file].lastModified == -2) {
                    // there was an exception while loading the file
                    INC[file].lastModified = 0; // force reload
                }
            }
        }
    }

    if (INC[file] == null) {
        throw "require('" + file + "') at " + __contextModule__ + ": '" + file + "' file not found";
    }

    if (isBeingExported) {
        // NOTE: we can't just use exports var here since it will always refer to _init.js' exports

        var hasAllowRedefine = "__allowRedefine__" in __contextExportsObj__;
        if ("__allowRedefine__" in INC[file].exports) {
            for (var k=Object.keys(INC[file].exports),i=0; i<k.length; i++) {
                // allow this module to redefine templates in its dependant exports object
                // This is useful for msg_... templates localization
                delete __contextExportsObj__[k[i]];
            }
        }

        // Regen exports using dependant' exportsObj as the new enclosed exports obj.
        // This is done rather than just copying exports into dependant's exports object,
        // so any closure references to the exports object are updated for the dependants one.
        INC[file].moduleFunc.call(__globalScope__, __contextExportsObj__, paramObj);

        if (!hasAllowRedefine)
            delete __contextExportsObj__.__allowRedefine__;

        // FIXME: mark all soy exports as html
        if (file.endsWith(".soy")) {
            var soyExports = Object.keys(__contextExportsObj__);

            for (var i = 0; i < soyExports.length; i++) {
                var soyExport = soyExports[i];

                if (typeof (__contextExportsObj__[soyExport]) == "function") {

                    __contextExportsObj__[soyExport] = (function() {
                            var origSoyFunc = __contextExportsObj__[soyExport];
                            var thiz = __contextExportsObj__;

                            delete __contextExportsObj__[soyExport];

                            return function() {
                                return Htm.html(origSoyFunc.apply(thiz, arguments));
                            }
                        })();
                }
            }
        }
    }

    return INC[file].exports;
}, INC);

var systemSettings = _r.config;

// root-dispatch-file should be defined in scriptable.properties
__globalScope__.__rootDispatchFile__ = "root-dispatch-file" in systemSettings?
    systemSettings["root-dispatch-file"] : "/_actions.js"

__globalScope__.__copyInitJsExports__ = function (e) {
    e.listSourceFilesCached = listSourceFilesCached;
    e.log = log;
    e.logInfo = logInfo;
    e.logEvent = logEvent;
    e.logError = logError;
    e.see = see;
    e.seeHtml = seeHtml;
    e.extend = extend;
    e.reloadUpdatedModules = reloadUpdatedModules;
}

// finally require the rest of the system
_r.scriptableExports = require("/scriptable/index.js");


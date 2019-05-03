var conf = _r.config;

// serve batched stylesheets
// Note: it's better for resources to have well-defined unique URL to improve caching
exports["bundle.css"] = function(r) {
    return sendCssBundle(r, "css");
};

function sendCssBundle(r, baseProp) {

    if (!__developmentMode__)
        return r.sendContent({ file: conf[baseProp + ".output"] });

    // load less lib only in dev
    require("/scriptable-less/index.js").runLessCompileTask(baseProp);

    return r.sendContent({ file: conf[baseProp + ".output"] });
};

// serve batched javascript files
exports["bundle.js"] = function(r) {
    return sendJsBundle(r, "js");
}

function sendJsBundle(r, baseProp) {

    if (__developmentMode__)
        r.runClientJsTranspileTask(baseProp);

    return r.sendContent({ file: conf[baseProp + ".output"] });

    /*
    var files = r.listSourceFilesCached(baseProp);
    var lastModified = Files.getLastModified(files);

    // callback is used to skip retrieving the files in case not-modified is returned
    var getData = function() {
        r.clearSourceFileListCache(baseProp);
        files = r.listSourceFilesCached(baseProp);

        return r.wrapAnon(baseProp, Files.getFilesAsString(files));
    }

    return r.sendContent({ type: "js", contentCallback: getData, lastModified: lastModified });
    */
}

// protects from sending unintended resources to client
var public_file_matchers = "public-files" in conf?
        Resources.getPathMatchers(conf["public-files"]) : null;

// serve various static files from view/ as long as they match public-files glob spec
exports["common"] = exports["view"] = function(r) {

    if (public_file_matchers == null)
        throw "Missing required property: 'public-files'";

    let file = "/scriptable" + r.originalActionPath;

    for (var i=0; i<public_file_matchers.length; i++) {

        if (Resources.isPathMatching(file, public_file_matchers[i])) {
            return r.sendContent({ file: file });
        }
    }

    return r.notFound();
}


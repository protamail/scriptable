var conf = _r.config;

// serve batched stylesheets
// Note: it's better for resources to have well-defined unique URL to improve caching
exports["bundle.css"] = function(r, p) {
    return sendCssBundle(r, p.id);
};

function sendCssBundle(r, baseProp) {
    if (__developmentMode__)
        // load less lib only in dev
        require("/scriptable/less/index.js").runLessCompileTask(baseProp);

    return r.sendContent({ file: conf[baseProp + ".output"] });
};

// serve batched javascript files
exports["bundle.js"] = function(r, p) {
    return sendJsBundle(r, p.id);
}

function sendJsBundle(r, baseProp) {
    if (__developmentMode__)
        r.runClientJsTranspileTask(baseProp);

    return r.sendContent({ file: conf[baseProp + ".output"] });
}

// protects from sending unintended resources to client
var public_file_matchers = "public-files" in conf?
        Resources.getPathMatchers(conf["public-files"]) : null;

// serve various static files from view/ as long as they match public-files glob spec
exports["~"] = function(r) {

    if (public_file_matchers == null)
        throw "Missing required property: 'public-files'";

    let file = "/" + r.actionArray.join("/"); // must start with slash for match to work

    for (var i=0; i<public_file_matchers.length; i++) {

        if (Resources.isPathMatching(file, public_file_matchers[i])) {
            return r.sendContent({ file: file });
        }
    }

    return r.notFound();
}

exports["favicon.ico"] = function(r, p, t) {
    return r.sendContent({ file: `${conf.appdir}/view/static/images/favicon.ico` });
}


var marked = require('marked.js');

var TRANSPILE_DEST = "/WEB-INF/transpiled/";

marked.marked.Renderer.prototype.heading = function (text, level) {
    return "<h" + level + ">" + text + "</h" + level + ">\n";
}

marked.marked.Renderer.prototype.paragraph = function (text) {
    return text.match(/^\s*\${/)? text : ("<p>" + text + "</p>\n");
}

/**
 * This function is used by Ant ScriptableCompileTask to compile Markdown files
 */
exports.runMdTranspileTask = sync(function(baseName, opt) {

    opt = opt || {};

    if (!__developmentMode__ && !__compilationMode__)
        throw "runLessCompileTask: should only be used in development or compilation mode";

    function listUpdatedFiles() {
        var files = _r.listSourceFilesCached(baseName);
        var updatedFiles = [];
        var pathMatcher = opt.extMatch? Resources.getPathMatcher(opt.extMatch) : null;

        for (var i=0,l=files.length; i<l; i++) { // find updated files
            var f = Files.getFile(files[i]);
            var dst = Files.getFile(TRANSPILE_DEST + files[i] + '.js');
            var func = files[i].substring(files[i].lastIndexOf("/") + 1, files[i].indexOf("."));

            if (dst.lastModified() < f.lastModified() &&
                    (!pathMatcher || Resources.isPathMatching("/" + f.name, pathMatcher)))
                updatedFiles.push([f, dst, func]);
        }
        return updatedFiles;
    }

    var updatedFiles = listUpdatedFiles();

    if (updatedFiles.length) {
        _r.clearSourceFileListCache(baseName);

        _r.genIndexFile(baseName);

        updatedFiles = listUpdatedFiles();

        logEvent("Transpiling " + updatedFiles.length + " " + baseName +
                (opt.extMatch? " " + opt.extMatch : "") + " file(s) to " + TRANSPILE_DEST);

        for (var f in updatedFiles) {
            var dst = updatedFiles[f][1];
            Files.mkdirs(dst, true); // writable by all
            var html = marked.marked(Files.getFileAsString(updatedFiles[f][0]), {});
            var name = _r.escapeJs(updatedFiles[f][2]);
            var camelizedName = Strings.camelize(name, '-');
            var result = interpolate(html, camelizedName, name);
            // NOTE: __allowRedefine__ is a special purpose export which tells scriptable that
            // .md generated exports are allowed to redefine already existing group exports
            // This is used for localization purposes
            result += 'exports["' + camelizedName + '"] = ' + camelizedName +
                ";\nexports.__allowRedefine__ = 1;\n";
            Files.writeStringToFile(result, dst);
            Files.makeWritableByAll(dst);
        }
    }
    else if (__compilationMode__)
        logEvent("No " + baseName + (opt.extMatch? " " + opt.extMatch : "") +
                " files changed, compilation skipped.");

    return updatedFiles.length;
});

// ${...} -- is interpolated following JS template semantics, unless it's escaped as \${...}
// MD document should assume the generated function takes a single parameter t,
// representing template parameter object, e.g. ${t.x}
//
function interpolate(html, camelizedName, name) {

    var vars = html.match(/\${.+?}/g) || [];
    var tmpl = html.split(/\${.+?}/g);

    for (var i = 0; i < tmpl.length; i++) {
        var ti = tmpl[i];

        if (ti.length > 0 && ti.indexOf("\\") == (ti.length - 1)) {
            ti = ti.substring(0, ti.length - 1) + vars[i];
            vars[i] = "${''}";//.splice(i, 1);
        }

        // wrap resulting HTML into a div with class matching .md file name sans extension
        tmpl[i] = (i == 0? '"' + Htm.escapeJs('<div class="' + Htm.escapeHtml(name) + '">') : '"') +
                Htm.escapeJs(ti) + (i == tmpl.length - 1? '</div>"' : '"');
    }

    for (var i = 0; i < vars.length; i++)
        // MD processor escapes HTML entities
        vars[i] = Htm.unescapeHtml(vars[i].substring(2, vars[i].length - 1));

    return 'function ' + camelizedName + '(t) { return Htm([' + tmpl.join(", ") + ']' +
        (vars.length > 0? ", " : "") + vars.join(", ") + ') };\n';
}


var conf = _r.config;
var jscomp = com.google.javascript.jscomp;

function getClosureCompilerOptions(baseName, defaultLevel, prettyPrint) {
    var co = new jscomp.CompilerOptions(), p;

    jscomp.WarningLevel.DEFAULT.setOptionsForWarningLevel(co); // VERBOSE|DEFAULT|QUIET
    co.setWarningLevel(jscomp.DiagnosticGroups.CHECK_USELESS_CODE, jscomp.CheckLevel.OFF);
//    co.checkSymbols = false;
//    co.setWarningLevel(jscomp.DiagnosticGroups.MISSING_PROPERTIES, jscomp.CheckLevel.OFF);
//    co.setWarningLevel(jscomp.DiagnosticGroups.NON_STANDARD_JSDOC, jscomp.CheckLevel.OFF);
//    co.setWarningLevel(jscomp.DiagnosticGroups.GLOBAL_THIS, jscomp.CheckLevel.OFF);

    p = baseName + '.compilationLevel'; // BUNDLE|WHITESPACE|SIMPLE|ADVANCED
    var compilationLevel = p in conf? conf[p] : (defaultLevel || "SIMPLE");
    var cl = jscomp.CompilationLevel.fromString(compilationLevel);

    p = baseName + '.prettyPrint';
    co.prettyPrint = p in conf? conf[p] : prettyPrint? true : compilationLevel == "BUNDLE"? true : false;

    cl.setOptionsForCompilationLevel(co);
    cl.setWrappedOutputOptimizations(co);

    // ES3|ES5|ES5_STRICT|ES6_TYPED|ES_2015|ES_2016|ES_2017|ES_NEXT and NO_TRANSPILE
    p = baseName + '.languageIn';
    co.languageIn = jscomp.CompilerOptions.LanguageMode.fromString(p in conf? conf[p] : "ES_NEXT");

    p = baseName + '.languageOut';
    co.languageOut = jscomp.CompilerOptions.LanguageMode.fromString(p in conf? conf[p] : "ES5_STRICT");

    return co;
}

exports.getMostRecentModified = function(files) {
    var newLastModified = 0;
    var glm = Files.getLastModified;
    for (var i=0, l=files.length; i<l; i++) {
        var t = glm(files[i]);
        if (t > newLastModified)
            newLastModified = t;
    }
    return newLastModified;
}

function getClosureCompileReport(result, compiler) {

    function getSourceSnippet(error) {
        var region = compiler.getSourceRegion(error.sourceName, error.lineNumber);
        var snippet = [];
        
        if (region != null) {
            snippet = region.getSourceExcerpt().split("\n");

            for (var i=region.beginningLineNumber; i<=region.endingLineNumber; i++)
                snippet[i - region.beginningLineNumber] = (i == error.lineNumber? "=> " : "   ") +
                    snippet[i - region.beginningLineNumber];
        }

        return error.description + " at " + error.sourceName + " line " +
            error.lineNumber + ":\n" + snippet.join("\n");
    }

    var report = "";

    if (result.warnings.length) {
        for (var i=0; i<result.warnings.length; i++) {
            report += "Warning: " + getSourceSnippet(result.warnings[i]);
        }
    }

    if (result.errors.length) {
        for (var i=0; i<result.errors.length; i++) {
            report += "Error: " + getSourceSnippet(result.errors[i]);
        }
    }

    return report;
}


exports.wrapAnon = function (baseName, source) {
    var result = "";
    var exportsProp = baseName + ".exportsVar";
    var exportsVar = (exportsProp in conf) && conf[exportsProp] || "window"

    if (exportsVar != 'window')
        result += 'window["' + exportsVar + '"]={};';

    // make this == window, assumed by google closure compiler
    result += "(function(exports){'use strict';" + source + "}).call(window, " + exportsVar + ");";

    return result;
};

/**
 * This function is used by Ant ScriptableCompileTask to minify client side javascript files
 */
exports.runClientJsTranspileTask = sync(function(baseName, opt) {
    opt = opt || {};

    function listUpdatedFiles(getLastModified) {
        var files = exports.listSourceFilesCached(baseName);
        var result = [];

        for (var i in files) {

//            if (files[i].startsWith('/'))
//                files[i] = files[i].substring(1);

            if (!getLastModified)
                result.push(jscomp.SourceFile.fromFile(Files.getFile(files[i])));
        }

        return getLastModified? exports.getMostRecentModified(files) : result;
   }

    var dataLastModified = listUpdatedFiles(true);
    var output = conf[baseName + '.output'];
    var outFile = Files.getFile(output);

    // at least create output dir/file, so tomcat has access to it in dev
    if (!dataLastModified || dataLastModified > Files.getLastModified(outFile)) {
        Files.mkdirs(outFile, true); // writable by all
        exports.clearSourceFileListCache(baseName);
        var updatedFiles = listUpdatedFiles();  // will get all files

        logEvent("Transpiling " + updatedFiles.length + " " + baseName + " file(s) to " + output);

        var co = getClosureCompilerOptions(baseName, opt.releasing? "SIMPLE" : "BUNDLE", !opt.releasing);
        co.setWarningLevel(jscomp.DiagnosticGroups.MISSING_PROPERTIES, jscomp.CheckLevel.OFF);
        co.setWarningLevel(jscomp.DiagnosticGroups.GLOBAL_THIS, jscomp.CheckLevel.OFF);

        // env: BROWSER|CUSTOM
        var externs = jscomp.CommandLineRunner.getBuiltinExterns(jscomp.CompilerOptions.Environment.BROWSER);

        var src = JavaUtil.toList.apply(JavaUtil, updatedFiles);
        var compiler = new jscomp.Compiler(new jscomp.BlackHoleErrorManager());
        var result = compiler.compile(externs, src, co);
        var report = getClosureCompileReport(result, compiler);

        if (result.success && report)
            console.log(report);
        else if (!result.success)
            throw report;

        var wrappedSrc = exports.wrapAnon(baseName, compiler.toSource());

        Files.writeStringToFile(wrappedSrc, outFile);
        Files.makeWritableByAll(outFile);
    }
    else if (opt.compilationMode)
        logEvent("No " + baseName + " files changed, compilation skipped.");
});

/**
 * This function is used by Ant ScriptableCompileTask to compile Soy template files
 */
exports.runSoyTranspileTask = sync(function(baseName, opt) {
    opt = opt || {};

    function listUpdatedFiles() {
        var updatedFiles = [];
        var files = exports.listSourceFilesCached(baseName);
        var pathMatcher = opt.extMatch? Resources.getPathMatcher(opt.extMatch) : null;

        for (var i=0,l=files.length; i<l; i++) { // find updated files
            var f = Files.getFile(files[i]);
            var dst = Files.getFile(TRANSPILE_DEST + files[i] + '.js');

            if (dst.lastModified() < f.lastModified() &&
                    (!pathMatcher || Resources.isPathMatching("/" + f.name, pathMatcher)))
                updatedFiles.push([f, dst]);
        }

        return updatedFiles;
    }

    var updatedFiles = listUpdatedFiles();

    if (updatedFiles.length) {
        // make sure new soy/md/etc modules get picked up
        exports.clearSourceFileListCache(baseName);

        exports.genIndexFile(baseName);

        updatedFiles = listUpdatedFiles();

        logEvent("Transpiling " + updatedFiles.length + " " + baseName +
                (opt.extMatch? " " + opt.extMatch : "") + " file(s) to " + TRANSPILE_DEST);

        var SoyJsSrcOptions = Packages.com.google.template.soy.jssrc.SoyJsSrcOptions;
        var SoyGeneralOptions = Packages.com.google.template.soy.shared.SoyGeneralOptions;
        var Guice = Packages.com.google.inject.Guice;
        var SoyModule = scriptable.template.soy.SoyModule;
        var SoyFileSet = Packages.com.google.template.soy.SoyFileSet;
        var injector = Guice.createInjector(SoyModule.getSoyModules());

        var opt = new SoyJsSrcOptions();
        opt.setShouldProvideRequireSoyNamespaces(false); // useless
        opt.setShouldGenerateGoogModules(false);
        opt.setShouldGenerateGoogMsgDefs(false);

        for (var f in updatedFiles) {
            var builder = injector.getInstance(SoyFileSet.Builder);
            // we compile files one at a time to prevent Soy compiler detecting template re-definitions
            builder.add(Files.getFileAsString(updatedFiles[f][0]), updatedFiles[f][0].toString());

            var js = builder.build().compileToJsSrc(opt, null).get(0)
                .replace(/if \(goog.DEBUG\) {[\s\S]*?}/gm, "")
                .replace(/goog.require.*$/gm, "")
                .replace(/' \+ \(\(goog.DEBUG .*?\).*?\) \+ '/gm, "")
                .replace(/\(\(goog.DEBUG .*?\).*?\)/gm, "''")
                .replace(/soy\.\$\$escapeHtml\(exports\./gm, "\(exports\.")
                .replace(/soydata.VERY_UNSAFE.ordainSanitizedHtml/gm, "");
            var dst = updatedFiles[f][1];
            Files.mkdirs(dst, true); // writable by all
            Files.writeStringToFile(js, dst);
            Files.makeWritableByAll(dst);
        }
    }
    else if (opt.compilationMode)
        logEvent("No " + baseName + (opt.extMatch? " " + opt.extMatch : "") +
                " files changed, compilation skipped.");

    return updatedFiles.length;
});

var TRANSPILE_DEST = "/WEB-INF/transpiled/";

/**
 * This function is used to convert ES6 Javascript to ES5 syntax
 */
exports.runServerJsTranspileTask = sync(function(baseName, opt) {
    opt = opt || {};

    function listUpdatedFiles() {
        var files = exports.listSourceFilesCached(baseName);
        var updatedFiles = [];
        var pathMatcher = opt.extMatch? Resources.getPathMatcher(opt.extMatch) : null;

        for (var i in files) {

            var destFile = Files.getFile(TRANSPILE_DEST + files[i] + '.js');
            var srcFile = Files.getFile(files[i]);

            if (destFile.lastModified() < srcFile.lastModified() &&
                    (!pathMatcher || Resources.isPathMatching("/" + srcFile.name, pathMatcher)))
                updatedFiles.push(jscomp.SourceFile.fromCode(files[i], Files.getFileAsString(srcFile)));
        }

        return updatedFiles;
    }

    var updatedFiles = listUpdatedFiles();
    var args = [];

    if (updatedFiles.length) {
        // make sure any new files get picked up
        exports.clearSourceFileListCache(baseName);

        exports.genIndexFile(baseName);

        updatedFiles = listUpdatedFiles();

        logEvent("Transpiling " + (opt.releasing? "(for release) " : "") + updatedFiles.length + " " +
                baseName + (opt.extMatch? " " + opt.extMatch : "") + " file(s) to " + TRANSPILE_DEST);

        Files.mkdirs(Files.getFile(TRANSPILE_DEST), true); // writable by all

        var co = getClosureCompilerOptions(baseName, opt.releasing? "SIMPLE" : "BUNDLE", true);
        var externs = jscomp.CommandLineRunner.getBuiltinExterns(jscomp.CompilerOptions.Environment.CUSTOM);

        // Note: we must compile files one at a time because we need to wrap them individually after compile,
        // otherwise generated global vars will clash unless we always re-compile complete file set
        for (var i=0; i<updatedFiles.length; i++) {
            var src = JavaUtil.toList.apply(JavaUtil, updatedFiles.slice(i, i+1));
            var compiler = new jscomp.Compiler(new jscomp.BlackHoleErrorManager());
            var result = compiler.compile(externs, src, co);
            var report = getClosureCompileReport(result, compiler);

            if (result.success && report)
                console.log(report);
            else if (!result.success)
                throw report;

            //var sourceArray = compiler.toSourceArray();

            var outputFile = Files.getFile(TRANSPILE_DEST + updatedFiles[i] + ".js");
            Files.mkdirs(outputFile, true); // writable by all
            var source = compiler.toSource();
                // FIXME: remove generated x.raw = [... template definitions (for now)
//                .replace(/\S+\.raw = \[(.*?)\];/gm, "");

            Files.writeStringToFile(source, outputFile);
            Files.makeWritableByAll(outputFile);
        }
    }
    else if (opt.compilationMode)
        logEvent("No " + baseName + (opt.extMatch? " " + opt.extMatch : "") +
                " files changed, compilation skipped.");

    return updatedFiles.length;
});

// sync it to avoid errors when browsers concurrently reload
exports.runGenericTranspileTask = sync(function(baseName, opt) {
    opt = opt || {};
    var baseNames = baseName.split(/\s+/), updateCount = 0, p, r;

    for (var i=0; i<baseNames.length; i++) {

        if ((p = baseNames[i]) && (p in conf)) { // property references other targets to run
            r = exports.runGenericTranspileTask(conf[p], opt);
            updateCount = updateCount + (r > 0? r : 0);
        }
        else if ((p = baseNames[i] + ".run") && (p in conf)) {
            // .run format: mod:func:[*.ext] mod:func:[*.ext] ...
            var runs = conf[p].split(/\s+/);

            for (var j=0; j<runs.length; j++) {
                var run = runs[j].split(/:/);

                // require the module even if only for side effects
                // path must be absolute here
                var m = require("/" + run[0]);

                if (run.length == 2 || run.length == 3) {

                    if (run.length == 3)
                        opt.extMatch = run[2];
                    else
                        delete opt.extMatch;

                    r = m[run[1]](baseNames[i], opt);
                }
//                else
//                    throw "Invalid entry in " + baseNames[i] + ".run: " + runs[j] + ", expected mod:func:[*.ext]";

                updateCount = updateCount + (r > 0? r : 0);
            }
        }
        else
            throw "Missing required property " + baseNames[i] + ".run: mod:func:[*.ext]";
    }

    return updateCount;
});

exports.genIndexFile = function (baseName) {
    var p = baseName + '.genIndexFile';
    var indexFile = p in conf? conf[p] : null;

    if (indexFile != null) {
        var files = exports.listSourceFilesCached(baseName);
        var genSource = "";

        for (var i=0; i<files.length; i++)
            genSource += "exports(\"" + Files.normalizePath(files[i]) + "\");\n"

        indexFile = Files.getFile(TRANSPILE_DEST + indexFile + ".js");
        Files.mkdirs(indexFile, true); // true = writable by all
        Files.writeStringToFile(genSource, indexFile);
        Files.makeWritableByAll(indexFile);
    }
}

/**
 * This function is used by Ant ScriptableCompileTask to compile server-side javascript to java class files
 */
exports.runServerJsCompileTask = sync(function(baseName, opt) {
    opt = opt || {};

    var files = Resources.listSourceFiles(TRANSPILE_DEST + "**.js");
    var env = new org.mozilla.javascript.CompilerEnvirons();
    env.initFromContext(org.mozilla.javascript.Context.getCurrentContext());
    env.generateDebugInfo = false; // for script error stack trace (also exposes dev paths), not needed in dev
    env.generatingSource = false; // don't include script source in class file
    env.optimizationLevel = 9;
//    env.strictMode = true;

    var compiler = org.mozilla.javascript.optimizer.ClassCompiler(env);
    var output = "WEB-INF/classes/";
    var updatedFiles = [], genClassNames = [], genClassFiles = [];

    for (var i=0,l=files.length; i<l; i++) { // find updated files
        var f = Files.getFile(files[i]);
        var className = _r.getGenClassName(files[i].substring(TRANSPILE_DEST.length - 1, files[i].length - 3));
        var classFile = Files.getFile(output + className.replace(/\./g, '/') + '.class');
        if (classFile.lastModified() < f.lastModified()) {
            updatedFiles.push(files[i]);
            genClassNames.push(className);
            genClassFiles.push(classFile);
        }
    }

    if (updatedFiles.length) {
        logEvent("Compiling " + updatedFiles.length + " transpiled js files to " + output);

        for (var i in updatedFiles) {
            var file = updatedFiles[i];
            var obj = compiler.compileToClassFiles(_r.wrapJsModule(Files.getFileAsString(file)),
                    file, 1, genClassNames[i]);

            for (var j=0,l=obj.length; j<l; j+=2) {
                var outfile = genClassFiles[i];
                Files.mkdirs(outfile, true); // true = writable by all
                Files.writeBytesToFile(obj[j+1], outfile);
                Files.makeWritableByAll(outfile);
            }
        }
    }
    else if (opt.compilationMode)
        logEvent("No transpiled js files changed, compilation skipped.");

    return updatedFiles.length;
});


/**
 * Procedural config initialization not available from or redefining scriptable.properties,
 * such as conditional assignment, object construction, etc.
 */

// recognize error code 20002 as validation error
//exports.DS_IPCAM = new DataSourceConfig("ipcam", 20002);

// Customize time zone for use in date formatting routines
org.scriptable.template.TemplateUtil.timeZone = "GMT";

exports.sessionCookieName = "ssid";
exports.sessionCache = new LruCache(256);


// specifies types of static resources that can be served from r.config["public-files"]
exports("scriptable/common/sendContent_allowed_file_types.js");

// refresh outdated scripts
exports["__beforeJsReload__"] = function() {
    var updateCount = _r.runGenericTranspileTask("sjs view");
};

exports.mailProperties = {
    mail_from: '"Sample app" <sample@scriptable.org>',
    mail_smtp_host: 'localhost',
    mail_smtp_port: 25,
    mail_smtp_timeout: 2000
}

// site specific logic goes here
try { exports("site.js") } catch (ignoreInCaseItsMissing) {}

//if (__developmentMode__)
//    htm.keepWs = true; // don't strip formatting whitespace in compiled js code

// finally, copy exports over to _r.config possibly redefining some scriptable.properties,
// and back, to make the same available via require("config.js")
_r.extend(_r.config, exports);
_r.extend(exports, _r.config);


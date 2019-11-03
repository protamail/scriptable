const conf = require("init.js");
const model = require(`${conf.appdir}/_model.js`);
const app = require(`${conf.appdir}/_actions.js`);
const view = require(`${conf.appdir}/@view.js`);

function getSession(sessionId, userId) {
    var session = {}//model.getSession(sessionId, userId);

    if (session.sessionId) {

        if (session.sessionDataJson != null) {
            try {
                session.sessionData = JSON.parse(session.sessionDataJson);
            }
            catch (e) {
                session.sessionData = {};
            }
        }

        if (session.sessionData == null)
            session.sessionData = {};
    }

    return session;
}

exports["__default__"] = function(r, p, t) {

    t.homeUrl = r.contextPath;
    t.actType = "ACCESS"; // default activity type
    t.doLog = false;

    // NOTE: we don't authenticate in __before__ handler because some static resources
    // need to be available even when not logged in (e.g. error pages),
    // also some browsers (IE) won't send cookies when requesting favicon.ico
    var sessionId = r.getPlainCookieValue(conf.sessionCookieName);
    var session = conf.sessionCache.get(sessionId);

    if (session == null)
        session = getSession(sessionId, null);

    if (!session.sessionId) {

        //r.setPlainCookie(conf.sessionCookieName, session.sessionId, 0);

        // NOTE: session is cached here rather than in getSession because sessionId is known at this point
        //conf.sessionCache.put(session.sessionId, session);
    }

    t.session = session;
    t.title = "Sample Scriptable app";

    t.doLog = true;
    return r.dispatchOn(app);
};

exports["__after__"] = function(r, p, t, respSoFar) {

    if (t.session && t.doLog) {

        // flatten session data for post processing to prevent possible concurrent access
        var session = t.session;
        var sessionDataJson = _r.toJSON(session.sessionData);

        if (sessionDataJson != session.sessionDataJson) {
            session.sessionDataJson = sessionDataJson;
        }
        else
            sessionDataJson = null; // no need to update

        t.__postprocessStrings = {
            requestLengthMillis: r.requestLengthMillis,
            sessionId: session.sessionId,
            sessionDataJson: sessionDataJson,
            userId: session.userId,
            userName: session.userName
        }
    }
}

/**
 * Out of band: log access, update session, etc.
 */
exports["__postprocess__"] = function(r, p, t) {
    var s = t.__postprocessStrings;

    if (s) {

//        if (s.sessionId && s.sessionDataJson)
//            model.updateSession(s.sessionId, s.userId, s.userName, s.sessionDataJson);

//        if (s.userId && t.actType)
//            model.logActivity(s.userId, t.actType, t.actRefId, t.actData? _r.toJSON(t.actData) : null);

        _r.logAccess(s.userId + " " + r.method + " " + r.requestUrlWithPost + " " + r.status +
            " \"" + r.userAgent + "\" " + (r.ajaxRequest? "ajax" : "-") + " " + s.requestLengthMillis + "ms");
    }
    else
        _r.logAccess("- " + r.method + " " + r.requestUrlWithPost + " " + r.status +
                     " \"" + r.userAgent + "\" " + (r.ajaxRequest? "ajax" : "-"));
}

// bundle and static resource handlers
exports("scriptable/common/resourceBundleAction.js");
exports("scriptable/common/customErrorAction.js", view);

exports["favicon.ico"] = function(r, p, t) {
    return r.sendContent({ file: `${conf.appdir}/view/static/favicon.ico` });
}


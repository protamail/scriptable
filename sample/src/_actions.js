const conf = require("init.js");
const model = require("cdmr/_model.js");
const cdmr = require("cdmr/_actions.js");
const view = require("cdmr/@view.js");

function getSession(sessionId, userId) {
    var session = model.getSession(sessionId, userId);

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

exports["__default__"] = function(r, { vpPid }, t) {

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

    if (!session.sessionId || vpPid && session.sessionData.vpPid != vpPid ||
        !('sessionDate' in session) || new Date().getDate() != session.sessionDate) {

        var login = new Packages.cdmr.extra.Login(r, "GBMETRIC", "ALL/");
        session.sessionDate = new Date().getDate();

        if (__developmentMode__)
            log("Validating access for " + login.userId);

        if (r.isCommitted() || login.userId == null)
            return r.ajaxRequest? r.notAuthorized() : ''; // redirected to global login or error page

        if (!session.sessionId || session.sessionId != login.sessionId) {
            // no session, different session, or VP changed
            session = getSession(null, login.userId);

            if (!session.sessionId) {
                session = {
                    userId: login.userId,
                    userName: view.capitalize(login.firstName + " " + login.lastName),
                    sessionData: {}
                }
            }

            session.sessionId = login.sessionId;
            session.userName = view.capitalize(login.firstName + " " + login.lastName);
            r.setPlainCookie(conf.sessionCookieName, session.sessionId, 0);

            // clear any saved assumed attuid on new session
            delete session.sessionData.assumeAttuid;

            let myDetails = model.listUserDetails(session.userId);

            if (myDetails.length && myDetails[0].salesOpsAccessType == "SUPER")
                session.sessionData.allowSwitchAttuid = true;
            else
                delete session.sessionData.allowSwitchAttuid;
        }

        // NOTE: session is cached here rather than in getSession because sessionId is known at this point
        conf.sessionCache.put(session.sessionId, session);
        session.sessionData.vpPid = vpPid;
    }

    t.session = session;
    t.title = "CDMR";

    t.doLog = true;
    return r.dispatchOn(cdmr);
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

        if (s.sessionId && s.sessionDataJson)
            model.updateSession(s.sessionId, s.userId, s.userName, s.sessionDataJson);

        if (s.userId && t.actType)
            model.logActivity(s.userId, t.actType, t.actRefId, t.actData? _r.toJSON(t.actData) : null);

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


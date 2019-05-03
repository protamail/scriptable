package model.extra;

import org.scriptable.RhinoHttpRequest;
import javax.servlet.http.Cookie;
import esGateKeeper.esGateKeeper;
import java.net.URLEncoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class Login {
    private String sessionId;
    private String attuid;

    public String getSessionId() {
        return sessionId;
    }

    public String getAttuid() {
        return attuid;
    }

    public Login(RhinoHttpRequest r, String from) throws IOException {
        String ess = r.getPlainCookieValue("attESSec");
        String eshr = r.getPlainCookieValue("attESHr");
        if (ess != null) {
            sessionId = ess.hashCode()+"";
            String decrypted = esGateKeeper.esGateKeeper(ess, "insightMerlin", "PROD");
            if (decrypted != null && !decrypted.equals("")) {
                String [] l = decrypted.split("\\|");
                if (l.length > 5)
                    attuid = l[5];
            }
        }

        if (eshr != null) {
            String [] l = eshr.split("%7c");
            if (l.length > 1) {
                firstName = l[0];
                lastName = l[1];
            }
        }

        if (attuid == null) {
            String location = "https://www.e-access.att.com/empsvcs/hrpinmgt/pagLogin?retURL=" +
                    getEncodedUrl() + "&sysName=insightMerlin";
            if (r.getHeader("X-Requested-With") != null) {
                r.setStatus(401); // return NOTAUTH to ajax requests, so page can be reloaded
                r.setHeader("Location", location);
            }
            else
                r.sendRedirectResponse(location);
            sessionId = null;
        }
        else {
            boolean gl = false;
            try {
                gl = new GLogin.loginHr("", r.getHttpServletRequest(), r.getHttpServletResponse(),
                        RhinoHttpRequest.getCurrentInstance().getHostRequestUrl(),
                        RhinoHttpRequest.isDevelopmentMode() ? "test" : "prod", "", "", from)
                    .get_result();
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new IOException(e.getMessage());
            }
            if (!gl) { // GLogin failed
                attuid = null;
                sessionId = null;
            }
        }
    }

    private String firstName, lastName;
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }

    public static String getEncodedUrl() throws IOException {
        return URLEncoder.encode(RhinoHttpRequest.getCurrentInstance().getHostRequestUrl(), "US-ASCII");
    }
}


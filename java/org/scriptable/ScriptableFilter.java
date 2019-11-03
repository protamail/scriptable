package org.scriptable;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletContext;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.scriptable.ScriptableHttpRequest;

import java.io.IOException;
import javax.servlet.ServletException;

// This is an alternative way to implement Scriptable library to the usual ScriptableServlet way
// It may be required when Scriptable is combined with other similar frameworks where each one
// is responsible for only part of the whole app.
public class ScriptableFilter implements Filter {
    static ServletContext srv;

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain filterChain)
        throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;

        new ScriptableHttpRequest(srv, request, response).keepQuietOn404().handleRequest();

        if (response.getStatus() == 404) {
            response.setStatus(200);
            filterChain.doFilter(request, response);
        }
    }

    public void destroy() {}

    public void init(FilterConfig config) {
        srv = config.getServletContext();
        // set document root here, so it's available before the first request, e.g. for websockets
        HttpRequest.setDocumentRoot(srv.getRealPath("/"));
    }
}

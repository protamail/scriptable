package org.scriptable;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletContext;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import org.scriptable.ScriptableHttpRequest;

import java.io.IOException;
import javax.servlet.ServletException;

// This is an alternative way to implement Scriptable library to the usual ScriptableServlet way
// It may be required when Scriptable is combined with other similar frameworks where each one
// is responsible for only part of the whole app.
public class ScriptableFilter implements Filter {
    static ServletContext srv;
    HttpServletRequest request = null;
    HttpServletResponse response = null;
    FilterChain filterChain = null;

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain f)
        throws IOException, ServletException {
        try {
            request = (HttpServletRequest)req;
            response = (HttpServletResponse)res;
            filterChain = f;
            new ScriptableHttpRequest(srv, request, response, this).handleRequest();
        }
        finally {
            request = null;
            response = null;
            filterChain = null;
        }
//        new ScriptableHttpRequest(srv, request, response).keepQuietOn404().handleRequest();

//        if (response.getStatus() == 404) {
//            response.setStatus(200);
//            filterChain.doFilter(request, response);
//        }
    }

    public String evalFilter() throws ServletException, IOException {
        if (request != null) {
            MyHttpServletResponseWrapper responseBuffer = new MyHttpServletResponseWrapper(response);
            filterChain.doFilter(request, responseBuffer);
            return responseBuffer.getContent();
        }
        else
            return null;
    }

    public static class MyHttpServletResponseWrapper extends HttpServletResponseWrapper {
        private ByteArrayOutputStream contentBuffer;
        private PrintWriter writer;
        public MyHttpServletResponseWrapper(HttpServletResponse res) {
            super(res);
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if(writer == null){
                contentBuffer = new ByteArrayOutputStream(4096);
                writer = new PrintWriter(contentBuffer);
            }
            return writer;
        }

        public String getContent() throws IOException {
            getWriter().flush();
            String xhtmlContent = new String(contentBuffer.toByteArray());
            return xhtmlContent;
        }
    }

    public void destroy() {}

    public void init(FilterConfig config) {
        srv = config.getServletContext();
        // set document root here, so it's available before the first request, e.g. for websockets
        HttpRequest.setDocumentRoot(srv.getRealPath("/"));
    }
}

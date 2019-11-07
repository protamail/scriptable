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
import javax.servlet.ServletOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import java.io.IOException;
import javax.servlet.ServletException;

// This is an alternative way to implement Scriptable library to the usual ScriptableServlet way
// It may be required when Scriptable is combined with other similar frameworks where each one
// is responsible for only part of the whole app.
public class ScriptableFilter implements Filter {
    // NOTE: any instance variables would be shared between different concurrent requests
    static ServletContext srv;

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain f)
        throws IOException, ServletException {
        new ScriptableRequest(srv, (HttpServletRequest)req, (HttpServletResponse)res, f).handleRequest();
    }

    public static String evalFilterChain(ServletRequest req, ServletResponse res, FilterChain f)
        throws ServletException, IOException {
        MyHttpServletResponseWrapper responseBuffer =
            new MyHttpServletResponseWrapper((HttpServletResponse)res);
        f.doFilter(req, responseBuffer);
        return responseBuffer.getContent();
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

/*        ServletOutputStream outputStream = null;

        @Override
        public javax.servlet.ServletOutputStream getOutputStream() throws IOException {
//            if(outputStream == null){
//                outputStream = new ByteArrayOutputStream(4096);
//            }
            return super.getOutputStream();
        }
*/

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

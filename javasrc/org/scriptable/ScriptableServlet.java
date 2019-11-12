package org.scriptable;

/**
 * The servlet implementation using ScriptableRequest as the request handler
 */

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.MultipartConfig;

import java.io.IOException;
import javax.servlet.ServletException;

@MultipartConfig
public class ScriptableServlet extends HttpServlet {
    // NOTE: any instance variables would be shared between different concurrent requests
    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response)
        throws ServletException {
        // for websocket
        // request.getSession();
        new ScriptableRequest(getServletContext(), request, response).handleRequest();
    }

    @Override
    public void init() throws ServletException {
        // set document root here, so it's available before the first request, e.g. for websockets
        try {
            HttpRequest.setDocumentRoot(getServletContext().getRealPath("/"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}


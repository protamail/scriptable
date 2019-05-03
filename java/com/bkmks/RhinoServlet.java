package com.bkmks;

/**
 * The servlet implementation using RhinoHttpRequest as the request handler
 */

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.MultipartConfig;

import javax.servlet.ServletException;

@MultipartConfig
public class RhinoServlet extends HttpServlet {
    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response)
        throws ServletException {
        // for websocket
        // request.getSession();
        new RhinoHttpRequest(getServletContext(), request, response).handleRequest();
    }

    @Override
    public void init() throws ServletException {
        // set document root here, so it's available before the first request, e.g. for websockets
        HttpRequest.setDocumentRoot(getServletContext().getRealPath("/"));
    }
}


package com.bkmks.util;

import java.io.DataInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import com.bkmks.template.TemplateUtil;
import java.net.URLEncoder;
import java.util.List;
import java.util.Iterator;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class Http
{
    public static String sendPostRequest(String href, List<String> headers, List<String> params,
            int timeoutMillis) throws IOException {
        URL url = new URL(href);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMillis);
            conn.setReadTimeout(timeoutMillis);
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true); // do POST
            Iterator<String> i = headers.iterator();
            while (i.hasNext())
                conn.setRequestProperty(i.next(), i.next());
            StringBuilder postBody = new StringBuilder();
            i = params.iterator();
            while (i.hasNext()) {
                postBody.append('&');
                postBody.append(i.next());
                postBody.append('=');
                postBody.append(URLEncoder.encode(i.next(), "UTF-8"));
            }
            if (postBody.length() > 0)
                postBody.deleteCharAt(0); // remove first &

            byte[] postContent = postBody.toString().getBytes();
            conn.setFixedLengthStreamingMode(postContent.length);
            conn.getOutputStream().write(postContent);
            DataInputStream input = new DataInputStream(conn.getInputStream());
            int contentLength = conn.getContentLength();
            if (contentLength >= 10000000)
                throw new IOException("response_too_long");
            if (contentLength > 0) {
                byte[] content = new byte[contentLength];
                input.readFully(content);
                return new String(content);
            }
            else {
                StringBuilder result = new StringBuilder();
                byte[] buf = new byte[2048];
                for (int len; (len = input.read(buf)) != -1 && result.length() < 10000000;)
                    result.append(new String(buf, 0, len));
                return result.toString();
            }
        }
        catch (IOException e) {
            int status = 0;
            if (conn != null) {
                try {
                    status = conn.getResponseCode();
                }
                catch (Exception j) {}
            }
            return "{\"error\": \"" + TemplateUtil.escapeJs(e.getMessage()) + "\", \"status\": " + status +
                status + ", \"href\": \"" + TemplateUtil.escapeJs(href) + "\"}";
        }
        finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public static String sendGetRequest(String href, int timeoutMillis)
        throws IOException {
        return sendGetRequest(href, timeoutMillis, 0);
    }

    public static String sendGetRequest(String href, int timeoutMillis, long ifModifiedSince)
        throws IOException {
        URL url = new URL(href);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(timeoutMillis);
            if (ifModifiedSince > 0)
                conn.setIfModifiedSince(ifModifiedSince);
            conn.setUseCaches(true);
            conn.setDoInput(true);
            conn.setDoOutput(false); // do GET
            DataInputStream input = new DataInputStream(conn.getInputStream());
            int contentLength = conn.getContentLength();
            if (contentLength >= 10000000)
                throw new IOException("response_too_long");
            if (conn.getResponseCode() == HttpServletResponse.SC_NOT_MODIFIED)
                throw new IOException("Not Modified");
            if (contentLength > 0) {
                byte[] content = new byte[contentLength];
                input.readFully(content);
                return new String(content);
            }
            else {
                StringBuilder result = new StringBuilder();
                byte[] buf = new byte[2048];
                for (int len; (len = input.read(buf)) != -1 && result.length() < 10000000;)
                    result.append(new String(buf, 0, len));
                return result.toString();
            }
        }
        catch (IOException e) {
            int status = 0;
            if (conn != null) {
                try {
                    status = conn.getResponseCode();
                }
                catch (Exception j) {}
            }
            return "{\"error\": \"" + TemplateUtil.escapeJs(e.getMessage()) + "\", \"status\": " +
                status + ", \"href\": \"" + TemplateUtil.escapeJs(href) + "\"}";
        }
        finally {
            if (conn != null)
                conn.disconnect();
        }
    }
}


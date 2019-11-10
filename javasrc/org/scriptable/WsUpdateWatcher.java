package org.scriptable;

import javax.websocket.Session;
import javax.websocket.RemoteEndpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.CloseReason;
import javax.websocket.MessageHandler;
import java.util.Map;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import org.scriptable.util.Resources;

import java.io.IOException;

public final class WsUpdateWatcher extends Endpoint {

    //static final ExecutorService singleThreadPool = HttpRequest.createFixedThreadPool(1);
    static final ThreadPoolExecutor singleThreadPool =
        (ThreadPoolExecutor) HttpRequest.createCachedThreadPool();

    Future watchThread = null;

    @Override
    public void onClose(Session session, CloseReason closeReason) {

        if (watchThread != null)
            watchThread.cancel(true); // true = allow interrupt
    }

    @Override
    public void onOpen(Session session, EndpointConfig econf) {

//        try{session.getBasicRemote().sendText("exit");}catch(IOException e){}
        // need to run blocking watcher out of WS session thread to get timely connection close notifications
        watchThread = singleThreadPool.submit(new Runnable() {

            @Override
            public void run() {

                RemoteEndpoint.Basic remote = session.getBasicRemote();
                Map config = ScriptableRequest.getConfig();

                if (config == null)
                    return;

                try {

                    remote.sendText("instanceId=" + HttpRequest.instanceId); // let them know when webapp reloaded

                    String p = ScriptableRequest.CONF_TARGETS_PROP;
                    StringBuilder include = new StringBuilder("scriptable.properties");

                    if (config.containsKey(p)) {

                        for (String baseName: config.get(p).toString().split(" ")) {
                            p = baseName + ".include";

                            if (config.containsKey(p)) {
                                include.append(" ");
                                include.append(config.get(p));
                            }
                        }

                        while (!Resources.waitForUpdates(include.toString(), 60) && session.isOpen()) {
                                remote.sendPing(ByteBuffer.wrap("ping".getBytes()));
                        }
                    }
                    else
                        throw new Exception("Missing " + ScriptableRequest.CONF_TARGETS_PROP +
                                " property in config");

                    if (session.isOpen()) {
                        remote.sendText("reload");
                    }
                }
                catch (InterruptedException e) {
                }
                catch (Throwable e) { // catch all, even class undef etc
                    HttpRequest.log("UpdateWatcher: " + e);
                }
                finally {

                    try {
                        session.close();
                    }
                    catch (IOException e) {
                    }
                }
            }
        });
    }
}


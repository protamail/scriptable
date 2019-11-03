package org.scriptable;

import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.Endpoint;
import java.util.Set;
import java.util.HashSet;
import org.scriptable.ScriptableHttpRequest;

public class WsServerConfig implements ServerApplicationConfig {

    @Override
    public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
        Set<ServerEndpointConfig> result = new HashSet<>();

        if (ScriptableHttpRequest.isDevelopmentMode()) { // deploy watcher endpoint in dev only

            for (Class ep: endpointClasses) {

                if (ep.equals(WsUpdateWatcher.class)) {
                    result.add(ServerEndpointConfig.Builder.create(ep, "/updateWatcher").build());
                }
            }
        }

        return result;
    }

    @Override
    public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
        // Let all @ServerEndpoint annotated endpoints be installed as they normally would
        return scanned;
    }
}


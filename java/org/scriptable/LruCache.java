package org.scriptable;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

public final class LruCache {

    Map<String, Map> lruCache;
    int cacheSize;

    public LruCache() {
        this(256);
    }

    public LruCache(int cacheSize) {
        this.cacheSize = cacheSize;

        // implements limited size cache with LRU-based eviction mechanism
        lruCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Map>(32, .75f, true /* use access ordered hash map */) {
                @Override
                protected synchronized boolean removeEldestEntry(Map.Entry<String, Map> eldest) {
                    return size() >= cacheSize; // limit LRU cache size
                }
            });
    }

    public Map get(String sessionId) {
        return lruCache.get(sessionId);
    }

    public void put(String sessionId, Map session) {
        if (cacheSize > 0) {
            if (session == null)
                lruCache.remove(sessionId);
            else
                lruCache.put(sessionId, session);
        }
    }
}


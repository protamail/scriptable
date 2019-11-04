package org.scriptable.util;

import java.util.List;
import java.util.ArrayList;

public final class JavaUtil {

    // Create List of unwrapped (when called from JS) java objects
    public static ArrayList toList(Object ...list) {
        ArrayList<Object> r = new ArrayList<>();
        for (Object l: list)
            r.add(l);
        return r;
    }
}


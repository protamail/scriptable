package com.bkmks.util;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import com.bkmks.template.TemplateUtil;
import com.bkmks.ScriptableMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;

final public class Json
{
    HashMap<Object, Object> visited = new HashMap<Object, Object>();
    StringBuilder buf = new StringBuilder();
    int nestLevel = 0;
    String tab = "    ";
    String indent = "";
    boolean decamelizeKeys = false;

    public Json(boolean decamelizeKeys) {
        this.decamelizeKeys = decamelizeKeys;
    }

    public static String toJSON(Object scope) {
        return toJSON(scope, false);
    }

    public static String toJSON(Object obj, boolean decamelizeKeys) {
        Json j = new Json(decamelizeKeys);
        j.serialize(obj, false, true, false);
        return j.toString();
    }

    public static String describe(Object obj) {
        Json j = new Json(false);
        j.serialize(obj, true, true, false);
        return j.toString();
    }

    public String toString() {
        return buf.toString();
    }

    void adjustIndent() {
        indent = "\n";
        for (int i=0; i<nestLevel; i++) {
            indent += tab;
        }
    }

    public static String getObjectId(Object v) { // prevent infinite loop with some Map object keys
        return v.getClass().getName() + "@" + System.identityHashCode(v);
    }

    void serialize(Object obj, boolean pretty, boolean last, boolean propContext) {

        if (obj == null || obj == Undefined.instance) {
            if (propContext && pretty)
                buf.append(' ');
            buf.append("null");
            if (!last)
                buf.append(',');
            return;
        }
        if (buf.length() > 10000000)
            throw new RuntimeException("toJSON: result exceeds maximum length");

        if (visited.containsKey(getObjectId(obj))) {
            buf.append("CIRC REF!");
            if (!last)
                buf.append(',');
        }
        else {
            if (!propContext && pretty)
                buf.append(indent);
            else if (pretty)
                buf.append(' ');

            if (obj instanceof Map) {
                buf.append('{');
            }
            else if (obj instanceof List || obj instanceof Object[]) {
                buf.append('[');
            }
            else {
                boolean noQuotes = !(obj instanceof CharSequence);
                if (obj instanceof Scriptable) {
                    String clazz = ((Scriptable)obj).getClassName();
                    if (clazz.equals("Number") || clazz.equals("Boolean"))
                        noQuotes = true;
                }

                if (pretty || noQuotes)
                    buf.append(obj + ""); // allow for null
                else {
                    buf.append('"');
                    buf.append(TemplateUtil.escapeJs(obj.toString(), true));
                    buf.append('"');
                }

                if (!last)
                    buf.append(',');
                return;
            }

            nestLevel++;
            adjustIndent();
            visited.put(getObjectId(obj), null);
            if (obj instanceof Map) {
                Map m = (Map) obj;
                int i = 0;
                Object[] keys = m.keySet().toArray();
                if (decamelizeKeys) {
                    for (int j=0; j<keys.length; j++)
                        keys[j] = Strings.decamelize(keys[j].toString());
                }
/*                if (keys.length == 0 && obj instanceof Scriptable) {
                    Scriptable proto = ((Scriptable)obj).getPrototype();
                    if (proto instanceof Map) {
                        m = (Map) proto;
                        keys = m.keySet().toArray();
                    }
                }*/

                for (Object k: keys) {
                    if (pretty) {
                        buf.append(indent);
                        buf.append(k.toString());
                    }
                    else {
                        buf.append('"');
                        buf.append(TemplateUtil.escapeJs(k.toString(), true));
                        buf.append('"');
                    }
                    buf.append(':');
                    serialize(m.get(k), pretty, ++i == keys.length, true);
                }
                if (pretty && (obj instanceof Scriptable) && ((Scriptable)obj).getPrototype() != null) {
                    Scriptable proto = ((Scriptable)obj).getPrototype();
                    if (proto.getIds().length > 0) {
                        buf.append(indent);
                        buf.append("__proto__:");
                        serialize(((Scriptable)obj).getPrototype(), pretty, true, true);
                    }
                }
            }
            else if (obj instanceof List) {
                List l = (List) obj;
                int i = 0;
                for (Object li: l) {
                    serialize(li, pretty, ++i == l.size(), false);
                }
            }
            else if (obj instanceof Object[]) {
                Object[] l = (Object[]) obj;
                int i = 0;
                for (Object li: l) {
                    serialize(li, pretty, ++i == l.length, false);
                }
            }
            nestLevel--;
            adjustIndent();
            visited.remove(getObjectId(obj));

            if (obj instanceof List || obj instanceof Object[]) {
                if (pretty)
                    buf.append(indent);
                buf.append(']');
                if (!last)
                    buf.append(',');
            }
            else if (obj instanceof Map) {
                if (pretty)
                    buf.append(indent);
                buf.append('}');
                if (!last)
                    buf.append(',');
            }
        }
    }
}


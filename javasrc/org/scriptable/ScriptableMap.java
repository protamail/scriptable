package org.scriptable;

/**
 * Javascript runtime compatible object with certain useful properties
 *
 * This is a customized implementation of Scriptable interface whith the following features:
 * - will throw an exception on non-existing property access
 * - will preserve the insertion order on key iteration
 * - an Undefined value will be converted to null before setting
 * - support array-like indexed access; indexed values are stored separately and are non-enumerable
 * - allow a default values object to be specified at construction time
 */

import org.scriptable.util.Json;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;
import java.util.Set;
import java.util.AbstractSet;
import java.util.Collection;

public final class ScriptableMap extends AbstractMap<String, Object> implements Function {
    boolean reportUndefRead = true;
    boolean reportRedefine = false;

    LinkedHashMap<String, Integer> staticKeys = null;
    Object[] staticValues = null;

    LinkedHashMap<String, Integer> dynamicKeys = null;
    ArrayList<Object> dynamicValues = null;

    NativeArray dynamicArray = null;

    Function func = null;

    public void setReportUndefRead(boolean v) {
        reportUndefRead = v;
    }

    public void setReportRedefine(boolean v) {
        reportRedefine = v;
    }

    public ScriptableMap() {
        dynamicKeys = new LinkedHashMap<>();
        dynamicValues = new ArrayList<>();
    }

    public ScriptableMap(String objectName) {
        this();
        this.objectName = objectName;
    }

    public ScriptableMap(boolean reportUndefRead) {
        this();
        this.reportUndefRead = reportUndefRead;
    }

    public ScriptableMap(String objectName, boolean reportUndefRead, boolean reportRedefine) {
        this();
        this.objectName = objectName;
        this.reportUndefRead = reportUndefRead;
        this.reportRedefine = reportRedefine;
    }

    public ScriptableMap(Function f, String objectName, boolean reportUndefRead, boolean reportRedefine) {
        this(objectName, reportUndefRead, reportRedefine);
        func = f;
    }

    public ScriptableMap(NativeArray v) {
        this();
        dynamicArray = v;
    }

    public ScriptableMap(NativeArray v, boolean reportUndefRead) {
        this(v);
        this.reportUndefRead = reportUndefRead;
    }

    public ScriptableMap(LinkedHashMap<String, Integer> staticKeys, Object[] staticValues,
            boolean reportUndefRead) {
        // this(); this is likely to be static-only map, so don't create dynamic storage yet
        this.staticKeys = staticKeys;
        this.staticValues = staticValues;
        this.reportUndefRead = reportUndefRead;
    }

/*    public ScriptableMap(Scriptable v) {
        super();

        if (v == this)
            throw ScriptRuntime.typeError("Attempt to use self as a parent scope");

        proto = v;
    }*/

    /**
     * Convert Scriptable object to ScriptableMap
     */
    public static ScriptableMap createFrom(Scriptable v) {
        ScriptableMap r = new ScriptableMap();

        for (Object o: v.getIds())
            r.put(o.toString(), v.get(o.toString(), v));

        return r;
    }

/*    public ScriptableMap(Scriptable v, boolean reportUndefRead) {
        this(v);
        this.reportUndefRead = reportUndefRead;
    }*/

//    protected int sealed = 0;

    /**
     * Prevent any further property changes
     * Use spin lock to make this recursive
     */
/*    public void seal() {
        sealed++;
    }

    public void unseal() {
        sealed--;
    }
*/

    @Override
    public String getClassName() {
        return this.getClass().toString();
    }

    /**
     * Helper method to get String properties without throwing exception on missing key
     */
    public String get(String name, String defaultValue) {

        if (!containsKey(name))
            return defaultValue;

        return get((String) name, this).toString();
    }

    @Override
    public Object get(Object name) {
        return get((String)name, this);
    }

/*
    static int intern = 0, ninter = 0, lastc = 0, interned = 0, internedd=0;
*/

    @Override
    public Object get(String name, Scriptable start) {
/*        
        boolean maybe = false;
        if (name.intern() != name)
            ninter++;
        else
            maybe = true;

        if (++intern - lastc > 1000) {
            lastc = intern;
            System.out.println(intern + " vs " + ninter + ", " + internedd);
        }
*/

        if (staticKeys != null) {
            Integer i = staticKeys.get(name);

            if (i != null)
                return staticValues[i];
        }

        if (dynamicKeys != null) { // dynamic keys dont't override any static ones, only augment
            Integer i = dynamicKeys.get(name);

            if (i != null)
                return dynamicValues.get(i);
        }

        Object val = Scriptable.NOT_FOUND;

        if (has("__noSuchProperty__", start) &&
            get("__noSuchProperty__", start) instanceof Function) {
            val = ScriptableRequest.callJsFunction((Function)this.get("__noSuchProperty__", start), name);
        }

        // raise EcmaError which includes script context information
        if (name.equals("undefined"))
            return Undefined.instance; // generate undefined object
        else if ((val == Undefined.instance || val == Scriptable.NOT_FOUND) && reportUndefRead &&
                // avoid cryptic error messages when accessing default properties
                !name.equals("__noSuchMethod__") && !name.equals("__noSuchProperty__"))
            throw ScriptRuntime.undefReadError(this.toString(), name);

        return val;
    }

    @Override
    public boolean containsKey(Object name) {

        if (dynamicKeys != null && dynamicKeys.containsKey(name) ||
            staticKeys != null && staticKeys.containsKey(name))
            return true;

        if (dynamicKeys != null && dynamicKeys.containsKey("__lazyProperties__") ||
            staticKeys != null && staticKeys.containsKey("__lazyProperties__")) {
            // NOTE: can't trigger lazy/async eval here since even property assignment checks for presence
            Scriptable lp = (Scriptable)get("__lazyProperties__");
            return lp.has(name.toString(), lp);
        }

        return false;
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return containsKey(name);
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return has(index + "", start);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        put(index + "", start, value);
    }

    public void put(int index, Object value) {
        put(index, this, value);
    }

    @Override
    public Object get(int index, Scriptable start) {

        if (dynamicArray != null && dynamicArray.size() > index)
            return dynamicArray.get(index);

        return get(index + "", start); 
    }

    public Object get(int index) {
        return get(index, this);
    }

    @Override
    public Object put(String name, Object value) {

//        if (sealed > 0)
//            throw ScriptRuntime.typeError("Can't modify property '" + name +
//                    "' in sealed object: " + this.toString());

        // interning here is justified because putting requires checking for key existance
        // which will offset this extra step overhead all by itself
        name = name.intern();

        if (reportRedefine && containsKey(name))
            throw ScriptRuntime.typeError(name + " is already defined in " + this.toString());

        if (value == Undefined.instance)
            value = null;

        if (staticKeys != null) { // replace static value if already exists
            Integer i = staticKeys.get(name);

            if (i != null) {
                Object r = staticValues[i];
                staticValues[i] = value;

                return r;
            }
        }

        if (dynamicKeys == null) { // otherwise modify dynamic value to preserve static keys
            dynamicKeys = new LinkedHashMap<>();
            dynamicValues = new ArrayList<>();
        }

        Integer i = dynamicKeys.get(name);

        if (i != null)
            return dynamicValues.set(i, value);

        dynamicKeys.put(name, dynamicValues.size());
        dynamicValues.add(value);

        return null;
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        put(name, value);
    }

    @Override
    public void delete(String name) {

        if (containsKey("__lazyProperties__")) {
            Scriptable lp = (Scriptable)get("__lazyProperties__");
            if (lp.has(name, lp))
                get(name); // finalize lazy/async properties first
            lp.delete(name);

            return;
        }

        if (dynamicKeys != null && dynamicKeys.containsKey(name))
            dynamicKeys.remove(name);
        else if (staticKeys != null)
            staticKeys.remove(name);
    }

    @Override
    public void delete(int index) {
        delete (index + "");
    }

    @Override
    public Scriptable getPrototype() {
        return null;
    }

    @Override
    public void setPrototype(Scriptable v) {
        throw ScriptRuntime.typeError("setPrototype is not supported by " + this.toString());
    }

    @Override
    public Scriptable getParentScope() {
        return parentScope;
    }

    @Override
    public void setParentScope(Scriptable v) {
        parentScope = v;
    }

    @Override
    public Object[] getIds() {
        // Object.keys(o) should return only own properties as opposed to 'in' operator

        if (staticKeys != null && dynamicKeys == null)
            return staticKeys.keySet().toArray();

        if (dynamicKeys != null && staticKeys == null)
            return dynamicKeys.keySet().toArray();

        if (staticKeys == null && dynamicKeys == null)
            return new Object[0];

        ArrayList<Object> result = new ArrayList<>(staticKeys.size() + dynamicKeys.size());

        for (Object o: staticKeys.keySet())
            result.add(o);

        for (Object o: dynamicKeys.keySet())
            result.add(o);

        return result.toArray();
    }

    @Override
    public Set<String> keySet() {
        // FIXME: Java 9 has handy Set.of(Object[]) to replace the following

        return new AbstractSet<String>() {
            Object[] keys = getIds();

            @Override
            public Iterator<String> iterator() {

                return new Iterator<String>() {
                    int i = 0;

                    @Override
                    public String next() {
                        return keys[i++].toString();
                    }

                    @Override
                    public boolean hasNext() {
                        return i < keys.length;
                    }
                };
            }

            @Override
            public int size() {
                return keys.length;
            }
        };
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        throw new UnsupportedOperationException();
    }

/*    @Override
    public Collection<Object> values() {
        throw ScriptRuntime.typeError("entrySet: operation not supported");
    }*/

    @Override
    public Object getDefaultValue(Class<?> hint) {
        return this.toString(); //shouldn't be Scriptable
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        return instance instanceof ScriptableMap;
    }

/*    private Map<String, String> altColMapping = null;
    public void setAltColumnMapping(Map<String, String> v) {
        altColMapping = v;
    }
*/

    private String objectName;

    public void setObjectName(String v) {
        objectName = v;
    }

    @Override
    public String toString() {
        return Json.getObjectId(this) + (objectName != null? (" [" + objectName + "]") : "");
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {

        if (func == null)
            throw ScriptRuntime.undefReadError(this.toString(), "()");

        return ScriptableRequest.callJsFunction(func, args);
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        throw ScriptRuntime.undefReadError(this.toString(), "Not a constructor function");
    }

    protected Scriptable parentScope = null;
}


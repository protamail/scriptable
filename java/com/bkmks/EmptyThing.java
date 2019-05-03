package com.bkmks;

import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.NativeSymbol;

// returns itself when dereferencing any of its properties, can be made iterable
// used as a default value for deep destructuring in template parameters

public class EmptyThing extends NativeObject {

    @Override
    public boolean avoidObjectDetection() { // make this a falsy value
        return true;
    }

    @Override
    public Object get(int idx, Scriptable start) {

        return this;
    }

    @Override
    public Object get(String name, Scriptable start) {

        if (name.equals(NativeSymbol.ITERATOR_PROPERTY)) // let Symbol.iterator property be read
            return super.get(name, start);

        return this;
    }

    @Override
    public void put(int idx, Scriptable start, Object val) {
    }

    @Override
    public void put(String name, Scriptable start, Object val) {

        if (name.equals(NativeSymbol.ITERATOR_PROPERTY)) // let Symbol.iterator property be assigned
            super.put(name, start, val);
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        return "";
    }

    @Override
    public String toString() {
        return "";
    }
}


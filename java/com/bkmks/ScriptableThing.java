package com.bkmks;

import com.bkmks.util.Json;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.ScriptRuntime;

public class ScriptableThing implements Scriptable {

    public ScriptableThing() {}

    @Override
    public String getClassName() {
        return this.getClass().toString();
    }

    @Override
    public Object get(String name, Scriptable start) {
        return Undefined.instance;
    }

    @Override
    public Object get(int index, Scriptable start) {
        return Undefined.instance;
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return false;
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return false;
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        throw ScriptRuntime.typeError("Operation not supported");
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        throw ScriptRuntime.typeError("Operation not supported");
    }

    @Override
    public void delete(String name) {
        throw ScriptRuntime.typeError("Operation not supported");
    }

    @Override
    public void delete(int index) {
        throw ScriptRuntime.typeError("Operation not supported");
    }

    @Override
    public Scriptable getPrototype() {
        return null;
    }

    @Override
    public void setPrototype(Scriptable v) {
        throw ScriptRuntime.typeError("Operation not supported");
    }

    @Override
    public Scriptable getParentScope() {
        return null;
    }

    @Override
    public void setParentScope(Scriptable v) {
        throw ScriptRuntime.typeError("Operation not supported");
    }

    @Override public Object[] getIds() {
        return new Object[0];
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        return this.toString(); //shouldn't be Scriptable
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        return instance instanceof Scriptable;
    }

    @Override
    public String toString() {
        return Json.getObjectId(this);
    }
}


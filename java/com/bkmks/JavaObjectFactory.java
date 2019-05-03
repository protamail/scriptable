package com.bkmks;

/**
 * Create some javascript types out of primitive components.
 * 
 * These utility methods can be used to create instances which will be recognized
 * by Rhino runtime as the corresponding javascript native types
 */

import java.util.Date;
import java.util.Calendar;
import java.util.List;
import java.text.DateFormat;
import java.sql.Timestamp;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class JavaObjectFactory
{
    /**
     * Create a Double from any number
     */
    public static Double createDouble(double v)
    {
        return Double.valueOf(v);
    }

    /**
     * Create a Date object from number of milliseconds since epoch
     */
    public static Date createDate(long millisSinceEpoch)
    {
        return new Timestamp(millisSinceEpoch);
    }

    /**
     * Create a Date object from string representation of number of milliseconds since epoch
     */
    public static Date createDate(String millisSinceEpoch)
    {
        return new Timestamp(Long.parseLong(millisSinceEpoch));
    }

    /**
     * Create a Date object from integer components: year: curYear-1900, month: 0-11, date: 1-31
     */
    public static Date createDate(int year, int month, int date)
    {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, date);
        return cal.getTime();
    }

    /**
     * Create a Date object from integer components
     */
    public static Date createDate(int year, int month, int date, int hour, int minute, int second)
    {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, date, hour, minute, second);
        return cal.getTime();
    }

    /**
     * Create Rhino JS array out of java array
     */
    public static Scriptable createJsArray(Object[] a) {
        return Context.getCurrentContext().newArray(RhinoHttpRequest.getGlobalScope(), a);
    }
}


package com.bkmks.template;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.Locale;
import java.util.List;
import java.util.regex.Pattern;
import com.bkmks.RhinoHttpRequest;
import com.bkmks.ScriptableThing;
import org.mozilla.javascript.Scriptable;

public final class TemplateUtil {

    static final class FormatTuple {

        public FormatTuple(Format f1, String f2, String f3, String f4, String f5) {
            format = f1;
            ifZero = f2;
            ifNull = f3;
            prefix = f4;
            suffix = f5;
        }

        public Format format;
        public String ifZero;
        public String ifNull;
        public String prefix;
        public String suffix;
    }

    static final ThreadLocal<HashMap<String, FormatTuple>> loCacheTL =
        new ThreadLocal<HashMap<String, FormatTuple>>();

    static {
        /**
         * Clear cached formats after each task/request (avoid tomcat leak warnings)
         */
        RhinoHttpRequest.threadLocalPerTaskCleaner.register(()-> {
            HashMap<String, FormatTuple> loCache = loCacheTL.get();

            if (loCache != null)
                loCache.clear();
        });
    }

    public static String format(Object value, String fmt) {
        HashMap<String, FormatTuple> loCache = loCacheTL.get();

        if (loCache == null) {
            loCache = new HashMap<String, FormatTuple>();
            loCacheTL.set(loCache);
        }

        FormatTuple ft = loCache.get(fmt);

        if (ft == null) {
            HashMap<String, String> params = parseFormat(fmt);
            String type = params.get("type");
            String digits = params.get("digits");
            String currencySymbol = params.get("currencySymbol");
            String pattern = params.get("pattern");
            String ifNull = params.get("ifnull");
            String ifZero = params.get("ifzero");
            String prefix = params.get("prefix");
            String suffix = params.get("suffix");

            NumberFormat numberFormat = null;

            numberFormat = type.equals("percent") ? NumberFormat.getPercentInstance() :
                             type.equals("currency") ? NumberFormat.getCurrencyInstance() :
                             type.equals("number") ? NumberFormat.getNumberInstance() : null;

            if (numberFormat == null)
                throw new IllegalArgumentException("Valid type values are percent, currency, or number");

            numberFormat.setRoundingMode(RoundingMode.HALF_UP);
            if (digits != null) {
                int n = Integer.parseInt(digits);
                numberFormat.setMinimumFractionDigits(n);
                numberFormat.setMaximumFractionDigits(n);
            }

            DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance();
            dfs.setCurrencySymbol(currencySymbol != null ? currencySymbol : "");
            ((DecimalFormat)numberFormat).setDecimalFormatSymbols(dfs);

            ft = new FormatTuple(numberFormat, ifZero, ifNull, prefix, suffix);
            loCache.put(fmt, ft);
        }

        if (value == null || !(value instanceof Number)) {
            value = null;
            return ft.ifNull;
        }

        if (ft.ifZero != null &&  ((Number)value).doubleValue() == 0)
            return ft.ifZero;

        String result = ft.format.format(value);

        if (ft.prefix != null)
            result = ft.prefix + result;

        if (ft.suffix != null)
            result += ft.suffix;

        return result;
    }

    public static String formatAny(Object value, String fmt) {
        HashMap<String, FormatTuple> loCache = loCacheTL.get();

        if (loCache == null) {
            loCache = new HashMap<String, FormatTuple>();
            loCacheTL.set(loCache);
        }

        FormatTuple ft = loCache.get(fmt);

        if (ft == null) {
            String[] f = fmt.split(";");
            Format format = null;
            String ifZero = null;
            String ifNull = null;

            // Note: we don't care if locale changes for different requests
            // since we cache formats for request duration only
            Locale locale = RhinoHttpRequest.getLocale();

            switch (f[0]) {
                case "decimal":
                    format = NumberFormat.getNumberInstance(locale);
                    break;
                case "percent":
                    format = NumberFormat.getPercentInstance(locale);
                    break;
                case "currency":
                    format = NumberFormat.getCurrencyInstance(locale);
                    break;
                case "date":
                    if (f.length > 2)
                        ifNull = f[2];
                    format = f.length > 1 && !f[1].equals("")?
                        new SimpleDateFormat(f[1], locale) : new SimpleDateFormat();
                break;
            }

            if (format == null)
                throw new RuntimeException("formatAny: format '" + f[0] + "' is not recognized");

            if (format instanceof DecimalFormat) {
                DecimalFormat df = (DecimalFormat)format;

                if (f.length > 2)
                    ifZero = f[2];

                if (f.length > 3)
                    ifNull = f[3];

                df.setRoundingMode(RoundingMode.HALF_UP);

                if (f.length > 1 && !f[1].isEmpty()) {

                    if (f[1].length() == 1 && Character.isDigit(f[1].charAt(0))) {
                        int digits = Integer.parseInt(f[1]);
                        df.setMinimumFractionDigits(digits);
                        df.setMaximumFractionDigits(digits);

                        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance();
                        dfs.setCurrencySymbol(""); // suppress currency symbol
                        df.setDecimalFormatSymbols(dfs);
                    }
                    else
                        df.applyPattern(f[1]);
                }
            }

            ft = new FormatTuple(format, ifZero, ifNull, null, null);
            loCache.put(fmt, ft);
        }

        if (value == null)
            return ft.ifNull;
        else if (!(value instanceof Number))
            return value.toString();
        else if (ft.ifZero != null && !ft.ifZero.isEmpty() && ((Number)value).doubleValue() == 0)
            return ft.ifZero;

        return ft.format.format(value);
    }

    static final Hashtable<String, SimpleDateFormat> dateFormats =
        new Hashtable<String, SimpleDateFormat>();

    public static String formatDate(Object val, String fmt)
    {
//        if (val == null || !(val instanceof java.util.Date))
//            return null;

        SimpleDateFormat dateFormat = dateFormats.get(fmt);
        if (dateFormat == null) {
            dateFormat = fmt != null && !fmt.equals("")? new SimpleDateFormat(fmt) : new SimpleDateFormat();
            if (timeZone != null)
                dateFormat.setTimeZone(timeZone);
            dateFormats.put(fmt, dateFormat);
        }

        synchronized(dateFormat) {
            return dateFormat.format(val);
        }
    }

    static TimeZone timeZone = null;
    /*
     * Set global time zone for use in date formatting
     * Time zone should be specified in the following format: "America/Los_Angeles"
     */
    public static void setTimeZone(String v) {
        timeZone = TimeZone.getTimeZone(v);
    }

    public static TimeZone getTimeZone(String v) {
        return timeZone;
    }

    /**
     * Parses format string of the form 'key1=value1 key2=value2 ...'
     * @param s Format string
     */
    public static HashMap<String, String> parseFormat(String s) {
        HashMap<String, String> result = new HashMap<String, String>();

        if (s.equals(""))
            return result;

        char[] characters = (s + ' ').toCharArray();
        String key = null;
        StringBuilder token = new StringBuilder();
        boolean inKey = false, inValue = false, inQuotes = false, escapeNext = false;

        for(int i = 0; i<characters.length; i++) {
            char ch = characters[i];

            if (escapeNext) {
                escapeNext = false;
                token.append(ch);
            }
            else if (ch == '\\') {
                escapeNext = true;
            }
            else if (ch == ' ' && inQuotes) {
                token.append(ch);
            }
            else if (ch == ' ') {
                if (inKey)
                    throw new IllegalArgumentException(
                            "Invalid format string, expecting = found 'space' (" + s + ")");
                else if (inValue) {
                    inValue = false;
                    result.put(key, token.toString());
                    token.setLength(0);
                }
            }
            else if (ch == '=' && inKey) {
                inKey = false;
                key = token.toString();
                token.setLength(0);
                inValue = true;
            }
            else if (ch == '"' && !inValue)
                throw new IllegalArgumentException("Invalid format string, misplaced \" (" + s + ")");
            else if (ch == '"') {
                if (!inQuotes) {
                    inQuotes = true;
                }
                else {
                    inQuotes = false;
                    inValue = false;
                    result.put(key, token.toString());
                    token.setLength(0);
                }
            }
            else if (!inValue) {
                inKey = true;
                token.append(ch);
            }
            else
                token.append(ch);
        }

        return result;
    }

    public static String escapeHtml(String s) {

        if (s == null ||
            s.indexOf('<') == -1 &&
            s.indexOf('"') == -1 &&
            s.indexOf('&') == -1)
            return s;

        StringBuilder sb = new StringBuilder(s.length() + 32);

        for(int i = 0; i < s.length();) {
            char c = s.charAt(i++);

            switch (c) {

                case '<':
                    sb.append("&lt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                default:
                    sb.append(c);
            }
        }

        return sb.toString();
    }

    // NOTE: while implementing Scriptable is not strictly necessary, it helps to avoid some warnings
    public static final class HtmlFragment extends ScriptableThing {

        public final String raw;

        public HtmlFragment(String s) {
            raw = s;
        }

        public HtmlFragment(HtmlFragment s) {
            this(s.toString());
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    static final String ws_stripped = "__scriptable__ws_stripped";
    public static boolean JS_TEMPLATE_STRIP_WS = true;

    public static HtmlFragment applyJsTemplate(Object[] args) {

        if (!(args[0] instanceof List))
            throw new IllegalArgumentException(
                    "Htm/applyJsTemplate: first parameter is expected to be of List type");

        List p = (List)args[0];

        if (p.size() == 1 && args.length == 1) // no interpolation required shortcut
            return new HtmlFragment(p.get(0).toString());

        Scriptable nativeArray = (Scriptable)p;
        StringBuilder sb = new StringBuilder();

        int i;

        if (!nativeArray.has(ws_stripped, nativeArray) && JS_TEMPLATE_STRIP_WS) {

            // collapse all newlines followed by space found in static content into ""
            synchronized (nativeArray) {
                Pattern pattern1 = Pattern.compile("\\n\\s+", Pattern.DOTALL);
                Pattern pattern2 = Pattern.compile("<!--.*?-->\\n?", Pattern.DOTALL);
                nativeArray.put(ws_stripped, nativeArray, true);

                for (i = 0; i < p.size(); i++) {
                    nativeArray.put(i, nativeArray,
                            pattern2.matcher(
                                // must keep newline to avoid joining tag and attribute from the next line
                                pattern1.matcher(nativeArray.get(i, nativeArray).toString()).replaceAll("\n")
                            ).replaceAll("")
                    );
                }
            }
        }

        String s;

        for (i = 0; i < p.size()-1;) {
            sb.append(p.get(i++)); // must preceed the rest
            Object arg = args[i];

            if (arg == null)
                continue;

            if (arg instanceof List) {

                for (Object a: (List)arg) {

                    if (a == null)
                        continue;

                    s = a.toString();

                    if (a instanceof HtmlFragment)
                        sb.append(s);
                    else if (!s.equals(""))
                        sb.append(escapeHtml(s));
                }
            }
            else {
                s = arg.toString();

                if (arg instanceof HtmlFragment)
                    sb.append(s);
                else if (!s.equals(""))
                    sb.append(escapeHtml(s));
            }
        }

        sb.append(p.get(i));

        return new HtmlFragment(sb.toString());
    }

    public static String unescapeHtml(String s) {
        if (s == null || s.indexOf('&') == -1)
            return s;

        s = s.replace("&lt;", "<");
        s = s.replace("&gt;", ">");
        s = s.replace("&quot;", "\"");
        s = s.replace("&#39;", "'");
        s = s.replace("&amp;", "&");

        return s;
    }

    public static String escapeJs(String s) {
        return escapeJs(s, false);
    }

    public static String escapeJs(String s, boolean dontEscapeSingleQuote) {
        if (s == null)
            return "";

        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c >= '0' && c <= 'z' && c != '\\')
                continue; // shortcircuit the common case

            if (c == '"' || c == '\'' || c == '\\' || c == '\b' || c == '\f' ||
                c == '\n' || c == '\r' || c == '\t' || c == '/' ||
                (c>='\u0000' && c<='\u001F') ||
                (c>='\u007F' && c<='\u009F') ||
                (c>='\u2000' && c<='\u20FF')) {

                StringBuilder sb = new StringBuilder(s.length() + 16);
                sb.append(s.subSequence(0, i));

                for(; i < s.length(); i++) {
                    c = s.charAt(i);

                    if (c >= '0' && c <= 'z' && c != '\\') { // shortcircuit the common case
                        sb.append(c);
                        continue;
                    }

                    switch (c) {
                        case '"':
                            sb.append("\\\"");
                            break;
                        case '\'':
                            sb.append(dontEscapeSingleQuote? "'" : "\\'");
                            break;
                        case '\\':
                            sb.append("\\\\");
                            break;
                        case '\b':
                            sb.append("\\b");
                            break;
                        case '\f':
                            sb.append("\\f");
                            break;
                        case '\n':
                            sb.append("\\n");
                            break;
                        case '\r':
                            sb.append("\\r");
                            break;
                        case '\t':
                            sb.append("\\t");
                            break;
                        case '/':
                            sb.append("\\/");
                            break;
                        default:
                            if ((c>='\u0000' && c<='\u001F') ||
                                (c>='\u007F' && c<='\u009F') ||
                                (c>='\u2000' && c<='\u20FF')) {

                                String ss = Integer.toHexString(c);
                                sb.append("\\u");
                                for(int k=0; k<4-ss.length(); k++){
                                    sb.append('0');
                                } 
                                sb.append(ss.toUpperCase());
                            }
                            else
                                sb.append(c);
                    }
                }

                return sb.toString();
            }
        }

        return s;
    }

    /**
     * Escape any non-printable/non-ascii characters using %uxxxx notation
     */
    public static String escapeUnicode(String s) {
        if (s == null)
            return "";

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c <= '\u001F' || c >= '\u007F') {
                StringBuilder sb = new StringBuilder(s.length() + 16);
                sb.append(s.subSequence(0, i));

                for(; i<s.length(); i++) {
                    c = s.charAt(i);

                    if (c <= '\u001F' || c >= '\u007F') {
                        String ss = Integer.toHexString(c);
                        sb.append("%u");

                        for(int k = 0; k < 4 - ss.length(); k++)
                            sb.append('0');

                        sb.append(ss.toUpperCase());
                    }
                    else
                        sb.append(c);
                }

                return sb.toString();
            }
        }

        return s;
    }

    /**
     * Unescape %XX and %uXXXX notation produced by the JavaScript's escape function
     */
    public static String unescapeUnicode(String s) {
        if (s == null || s.indexOf('%') == -1)
            return s;

        StringBuilder unicode = new StringBuilder(1024);
        StringBuilder sb = new StringBuilder(4);
        boolean hadPct = false;
        boolean inUnicode = false;
        boolean inByte = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (inUnicode) {
                sb.append(c);

                if (sb.length() == 4) {
                    // sb now contains the four hex digits
                    // which represents our unicode character
                    try {
                        int value = Integer.parseInt(sb.toString(), 16);
                        unicode.append((char) value);
                    }
                    catch (Exception e) { } // skip bad char
                        sb.setLength(0);
                        inUnicode = false;
                        hadPct = false;
                }
                continue;
            }
            else if (inByte) {
                sb.append(c);

                if (sb.length() == 2) {
                    try {
                        int value = Integer.parseInt(sb.toString(), 16);
                        unicode.append((char) value);
                    }
                    catch (Exception e) { } // skip bad char
                        sb.setLength(0);
                        inByte = false;
                        hadPct = false;
                }
                continue;
            }

            if (hadPct) {
                // handle an escaped value
                hadPct = false;

                if (c == 'u')
                    inUnicode = true;
                else {
                    sb.append(c);
                    inByte = true;
                }

                continue;
            }
            else if (c == '%') {
                hadPct = true;
                continue;
            }

            unicode.append(c);
        }

        return unicode.toString();
    }
}


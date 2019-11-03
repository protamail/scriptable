package org.scriptable.util;

import java.util.List;

final public class Strings {
    /**
     * Convert underscore-separated label into camel case label,
     * e.g. last_name to lastName
     */
    public static String camelize(String s) {
        return camelize(s, '_');
    }

    /**
     * Convert sep-separated label into camel case label,
     * e.g. last-name to lastName
     */
    public static String camelize(String s, char sep) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean capitalize = false;
        char[] characters = s.toCharArray();
        for(int i = 0; i<characters.length; i++) {
            char ch = characters[i];
            if (ch == sep) {
                capitalize = true;
                continue;
            }

            if (capitalize) {
                capitalize = false;
                ch = Character.toUpperCase(ch);
            }
            else
                ch = Character.toLowerCase(ch);

            sb.append(ch);
        }
        return sb.toString();
    }

    public static String decamelize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        char[] characters = s.toCharArray();
        for(int i = 0; i<characters.length; i++) {
            char ch = characters[i];
            if (Character.isUpperCase(ch)) {
                sb.append('_');
                ch = Character.toLowerCase(ch);
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Convert dash or space separated label into capitalized phrase,
     * e.g. "dash-separated label" to "Dash-Separated Label"
     */
    public static String capitalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean capitalize = true;
        char[] characters = s.toCharArray();
        for(int i = 0; i<characters.length; i++) {
            char ch = characters[i];
            if (ch == '-' || ch == ' ' || i == 0) {
                capitalize = true;
                if (i != 0) {
                    sb.append(ch);
                    continue;
                }
            }

            if (capitalize) {
                capitalize = false;
                ch = Character.toUpperCase(ch);
            }
            else
                ch = Character.toLowerCase(ch);

            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Some utility functions to be accessible via JS String object
     */
    public static boolean startsWith(String s, String ss) {
        return s.startsWith(ss);
    }

    public static boolean endsWith(String s, String ss) {
        return s.endsWith(ss);
    }

    public static String substringBefore(String s, String ss) {
        int c = s.indexOf(ss);
        return c == -1 ? "" : s.substring(0, c);
    }

    public static String substringBeforeLast(String s, String ss) {
        int c = s.lastIndexOf(ss);
        return c == -1 ? "" : s.substring(0, c);
    }

    public static String substringAfter(String s, String ss) {
        int c = s.indexOf(ss);
        return c == -1 ? "" : s.substring(c+ss.length());
    }

    public static String substringAfterLast(String s, String ss) {
        int c = s.lastIndexOf(ss);
        return c == -1 ? "" : s.substring(c+ss.length());
    }

    /**
     * Concatenate num of copies of the input string
     */
    public static String dupString(String input, int num) {
        StringBuilder builder = new StringBuilder(input.length()*num);
        for (int i=0; i<num; i++)
            builder.append(input);
        return builder.toString();
    }

    /**
     * Return number of occurrences of substr in the input string
     */
    public static int numberOfOccurrences(String input, String substr) {
        int i = -1, num = 0;
        while ((i = input.indexOf(substr, i+1)) != -1)
            num++;
        return num;
    }

    public static String[] toArray(List list) {
        String[] arr = new String[list.size()];
        int i = 0;
        for (Object l: list)
            arr[i++] = l.toString();
        return arr;
    }

}


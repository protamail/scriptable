package org.scriptable.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;
import org.scriptable.template.TemplateUtil;

/**
 * Returns first arg formatted according to the second arg format string
 * Use syntax: {decimal($p1, '.3')|id}
 */
class FormatFunction
{
    public static class Format implements SoyJsSrcFunction {
        final String prefix, fname;

        public Format(String prefix, String fname) {
            this.prefix = prefix;
            this.fname = fname;
        }

        @Override public String getName() {
            return fname;
        }

        @Override public Set<Integer> getValidArgsSizes() {
            return ImmutableSet.of(1, 2);
        }

        @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
            String arg1 = args.get(0).getText();
            String arg2 = args.size() > 1? args.get(1).getText() : "";

            if (arg2.startsWith("'") && arg2.length() > 1) {
                arg2 = arg2.substring(1, arg2.length()-1);
                arg2 = prefix + ";" + arg2;
                // test format string syntax at compile time
                TemplateUtil.formatAny(.0, arg2);
                return new JsExpr("soy.$$formatAny(" + arg1 + ", '" + TemplateUtil.escapeJs(arg2) + "')",
                        Integer.MAX_VALUE);
            }
            else {
                // format is specified as a variable reference
                return new JsExpr("soy.$$formatAny(" + arg1 + ", '" + prefix + ";'+" + arg2 + ")",
                        Integer.MAX_VALUE);
            }
        }
    }

    @Singleton final public static class Number extends Format {
        @Inject public Number() { super("decimal", "number"); }
    }

    @Singleton final public static class Percent extends Format {
        @Inject public Percent() { super("percent", "percent"); }
    }

    @Singleton final public static class Currency extends Format {
        @Inject public Currency() { super("currency", "currency"); }
    }

    @Singleton final public static class Date extends Format {
        @Inject public Date() { super("date", "date"); }
    }
}


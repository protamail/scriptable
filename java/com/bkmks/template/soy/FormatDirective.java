package com.bkmks.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.bkmks.template.TemplateUtil;
import java.util.List;
import java.util.Set;

/**
 * A directive that formats printed number or date as specified by its arguments
 */
public class FormatDirective {

    private static JsExpr applyForJsSrc(String prefix, JsExpr str, List<JsExpr> args) {
        String fmt = "";
        if (args.size() > 0)
            fmt = args.get(0).getText();
        if (fmt.startsWith("'") && fmt.length() > 1) {
            fmt = fmt.substring(1, fmt.length()-1);
            fmt = prefix + ";" + fmt;
            TemplateUtil.formatAny(.0, fmt); // test it out at compile time
            return new JsExpr("soy.$$formatAny(" + str.getText() + ", '" + TemplateUtil.escapeJs(fmt) + "')",
                    Integer.MAX_VALUE);
        }
        else { // format is specified as a variable
            return new JsExpr("soy.$$formatAny(" + str.getText() + ", '" + prefix + ";'+" + fmt + ")",
                    Integer.MAX_VALUE);
        }
    }

    public static class Format implements SoyJsSrcPrintDirective {
        final String prefix;

        public Format(String prefix) {
            this.prefix = prefix;
        }

        @Override public String getName() {
            return "|" + prefix;
        }

        @Override public Set<Integer> getValidArgsSizes() {
            return ImmutableSet.of(0, 1);
        }

        @Override public boolean shouldCancelAutoescape() {
            return true;
        }

        @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args) {
            return FormatDirective.applyForJsSrc(prefix, str, args);
        }
    }

    @Singleton final public static class Decimal extends Format {
        @Inject public Decimal() { super("decimal"); }
    }

    @Singleton final public static class Percent extends Format {
        @Inject public Percent() { super("percent"); }
    }

    @Singleton final public static class Currency extends Format {
        @Inject public Currency() { super("currency"); }
    }

    @Singleton final public static class Date extends Format {
        @Inject public Date() { super("date"); }
    }

}


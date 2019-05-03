package com.bkmks.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Soy function to unescape %uhhhh unicode wide character notation produced by the JS escape function
 */
@Singleton
class UnescapeFunction implements SoyJsSrcFunction
{
    @Inject UnescapeFunction() {
    }

    @Override public String getName() {
        return "unescape";
    }

    @Override public Set<Integer> getValidArgsSizes() {
        return ImmutableSet.of(1);
    }

    @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
        String arg1 = args.get(0).getText();
        String expr = "((opt_ijData_deprecated=" + arg1 + ")==null?null:soy.$$unescapeUnicode(opt_ijData_deprecated))";
        return new JsExpr(expr, Integer.MAX_VALUE);
    }
}


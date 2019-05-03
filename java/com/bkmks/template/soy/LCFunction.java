package com.bkmks.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Returns first arg converted to lower case
 * Use syntax: {lc($p1)}
 */
@Singleton
class LCFunction implements SoyJsSrcFunction
{
    @Inject LCFunction() {
    }

    @Override public String getName() {
        return "lc";
    }

    @Override public Set<Integer> getValidArgsSizes() {
        return ImmutableSet.of(1);
    }

    @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
        String arg1 = args.get(0).getText();
        String expr = "((opt_ijData_deprecated=" + arg1 + ")==null?null:opt_ijData_deprecated.toLowerCase())";
        return new JsExpr(expr, Integer.MAX_VALUE);
    }
}


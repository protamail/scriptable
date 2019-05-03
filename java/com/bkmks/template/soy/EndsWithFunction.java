package com.bkmks.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Checks if the first arg string ends with the second arg
 * Use syntax: {endsWith($p1, 'tail')}
 */
@Singleton
class EndsWithFunction implements SoyJsSrcFunction
{
    @Inject EndsWithFunction() {
    }

    @Override public String getName() {
        return "endsWith";
    }

    @Override public Set<Integer> getValidArgsSizes() {
        return ImmutableSet.of(2);
    }

    @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
        String arg1 = args.get(0).getText();
        String arg2 = args.get(1).getText();
        String expr = "((opt_ijData_deprecated=" + arg1 + ")!=null && (opt_ijData_deprecated.length-opt_ijData_deprecated.indexOf(" +
            arg2 + ")==" + arg2 + ".length && opt_ijData_deprecated.length>=" + arg2 + ".length))";
        return new JsExpr(expr, Integer.MAX_VALUE);
    }
}


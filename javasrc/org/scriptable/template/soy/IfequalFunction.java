package org.scriptable.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Returns third arg if the first one is equal to the second one, return the first arg otherwise
 * Use syntax: {ifequal($p1, $p2, 'equal')}
 */
@Singleton
class IfequalFunction implements SoyJsSrcFunction
{
    @Inject IfequalFunction() {
    }

    @Override public String getName() {
        return "ifequal";
    }

    @Override public Set<Integer> getValidArgsSizes() {
        return ImmutableSet.of(3);
    }

    @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
        String arg1 = args.get(0).getText();
        String arg2 = args.get(1).getText();
        String arg3 = args.get(2).getText();
        String expr = "((opt_ijData_deprecated=" + arg1 + ")==" + arg2 + "?" + arg3 + ":opt_ijData_deprecated)";
        return new JsExpr(expr, Integer.MAX_VALUE);
    }
}


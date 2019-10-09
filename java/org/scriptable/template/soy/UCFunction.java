package org.scriptable.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Returns first arg converted to upper case
 * Use syntax: {uc($p1)}
 */
@Singleton
class UCFunction implements SoyJsSrcFunction
{
    @Inject UCFunction() {
    }

    @Override public String getName() {
        return "uc";
    }

    @Override public Set<Integer> getValidArgsSizes() {
        return ImmutableSet.of(1);
    }

    @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
        String arg1 = args.get(0).getText();
        String expr = "((opt_ijData_deprecated=" + arg1 + ")==null?null:opt_ijData_deprecated.toUpperCase())";
        return new JsExpr(expr, Integer.MAX_VALUE);
    }
}


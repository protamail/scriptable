package org.scriptable.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Returns second arg if the first one is null or ''
 * Use syntax: {ifempty($p1, 'nil')}
 */
@Singleton
class IfemptyFunction implements SoyJsSrcFunction
{
    @Inject IfemptyFunction() {
    }

    @Override public String getName() {
        return "ifempty";
    }

    @Override public Set<Integer> getValidArgsSizes() {
        return ImmutableSet.of(2);
    }

    @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
        String arg1 = args.get(0).getText();
        String arg2 = args.get(1).getText();
        String expr = "((opt_ijData_deprecated=" + arg1 + ")==null||opt_ijData_deprecated==''?" + arg2 + ":opt_ijData_deprecated)";
        return new JsExpr(expr, Integer.MAX_VALUE);
    }
}


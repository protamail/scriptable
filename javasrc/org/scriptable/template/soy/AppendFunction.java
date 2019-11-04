package org.scriptable.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Soy function that appends its first parameter to the second one
 */
@Singleton
class AppendFunction implements SoyJsSrcFunction
{
    final static String OPT_DATA = "opt_data.";

    @Inject AppendFunction()
    {
    }

    @Override public String getName()
    {
        return "append";
    }

    @Override public Set<Integer> getValidArgsSizes()
    {
        return ImmutableSet.of(2);
    }

    @Override public JsExpr computeForJsSrc(List<JsExpr> args)
    {
        String arg1 = args.get(0).getText();
        String arg2 = args.get(1).getText();
        String expr = "(" + arg2 + "=" + arg2 + "||''," + arg2 + "+=" + arg1 + ",'')";
        return new JsExpr(expr, Integer.MAX_VALUE);
    }
}


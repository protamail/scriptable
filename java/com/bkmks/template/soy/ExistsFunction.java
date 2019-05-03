package com.bkmks.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Soy function that checks the existance of an input parameter (prevents undef read errors)
 * Use syntax: {if exists($p1.a[b].c.d)}
 */
@Singleton
class ExistsFunction implements SoyJsSrcFunction
{
    final static String OPT_DATA = "opt_data";

    @Inject ExistsFunction() {
    }

    @Override public String getName() {
        return "exists";
    }

    @Override public Set<Integer> getValidArgsSizes() {
        return ImmutableSet.of(1);
    }

    /**
     * Parses and converts expression like a.b.c or a.b[c] into (a in b && c in a.b)
     */
    @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
        return new JsExpr("(" + compile(args.get(0).getText(), null) + ")", Integer.MAX_VALUE);
    }

    private String compile(String arg, String result) {
        String prop;
        int i = arg.lastIndexOf('[');
        if (i != -1 && arg.endsWith("]"))
            prop = arg.substring(i+1, arg.length()-1);
        else if ((i = arg.lastIndexOf('.')) != -1)
            prop = "'" + arg.substring(i+1) + "'";
        else if (result == null)
            throw new IllegalArgumentException("Expected a template parameter reference, but received: " + arg);
        else
            return result;

        String remain = arg.substring(0, i);
        return compile(remain, "(" + (remain.indexOf(".") == -1 && remain.indexOf("[") == -1?
                        "(opt_ijData_deprecated=" + remain + ")!=null && " : "") +
                prop + " in opt_ijData_deprecated && " + "(opt_ijData_deprecated=opt_ijData_deprecated[" + prop + "])!=null)" +
                (result == null? "" : " && " + result));
    }
}


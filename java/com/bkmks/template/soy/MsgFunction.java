package com.bkmks.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Returns result of the localized version of a template specified in the first arg,
 * followed by optional template parameters
 * Use syntax: {msg('msg_tmpl_name', $p1)|id}
 */
@Singleton
class MsgFunction implements SoyJsSrcFunction
{
    @Inject MsgFunction() {
    }

    @Override public String getName() {
        return "msg";
    }

    @Override public Set<Integer> getValidArgsSizes() {
        return ImmutableSet.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
    }

    @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
        String arg1 = args.get(0).getText();
        String expr = "(exports.lang_en." + arg1 + "(";
        for (int i=1; i<args.size(); i++) {
            String arg2 = args.get(i).getText();
            expr += arg2;
            if (i<args.size()-1)
                expr += i<args.size()-1? ", " : ")";
        }
        return new JsExpr(expr, Integer.MAX_VALUE);
    }
}


package com.bkmks.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.bkmks.template.TemplateUtil;
import java.util.List;
import java.util.Set;

/**
 * |id - Disable autoescaping
 */
@Singleton
public class IdDirective implements SoyJsSrcPrintDirective {

    @Override public String getName()
    {
        return "|id";
    }

    @Override public Set<Integer> getValidArgsSizes()
    {
        return ImmutableSet.of(0);
    }

    @Override public boolean shouldCancelAutoescape()
    {
        return true;
    }

    @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args)
    {
        String value = str.getText();
        return new JsExpr("(" + value + ")", Integer.MAX_VALUE);
    }
}


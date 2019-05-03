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
 * |uc - Convert string to upper case
 */
@Singleton
public class UCDirective implements SoyJsSrcPrintDirective
{
    @Override public String getName()
    {
        return "|uc";
    }

    @Override public Set<Integer> getValidArgsSizes()
    {
        return ImmutableSet.of(0);
    }

    @Override public boolean shouldCancelAutoescape()
    {
        return false;
    }

    @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args)
    {
        String value = str.getText();
        return new JsExpr("((opt_ijData_deprecated=" + value + ")==null?null:opt_ijData_deprecated.toUpperCase())", Integer.MAX_VALUE);
    }
}


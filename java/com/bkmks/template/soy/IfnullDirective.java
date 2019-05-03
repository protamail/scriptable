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
 * |ifnull: 'str' - Replace null/undefined values with str
 */
@Singleton
public class IfnullDirective implements SoyJsSrcPrintDirective
{
    final static String OPT_DATA = "opt_data.";
    final static String ESCAPE = "soy.$$escapeHtml";

    @Inject public IfnullDirective()
    {
    }

    @Override public String getName()
    {
        return "|ifnull";
    }

    @Override public Set<Integer> getValidArgsSizes()
    {
        return ImmutableSet.of(1);
    }

    @Override public boolean shouldCancelAutoescape()
    {
        return false;
    }

    @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args)
    {
        String value = str.getText();
        // strip autoescape if present
        String escape = value.startsWith(ESCAPE) ? ESCAPE : "";
        if (!escape.equals(""))
            value = value.substring(ESCAPE.length());
        String replacement = args.get(0).getText();
        if (replacement.indexOf('.') < 0 && !replacement.startsWith("'"))
            replacement = "'" + TemplateUtil.escapeJs(replacement) + "'";
        // make sure we pass null (instead of some sort of Undefined object cast to String)
        return new JsExpr("((opt_ijData_deprecated=" + value + ")==null?" + replacement + ":" + escape + "(opt_ijData_deprecated))",
                Integer.MAX_VALUE);
    }
}


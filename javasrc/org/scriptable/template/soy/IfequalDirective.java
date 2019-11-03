package org.scriptable.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import org.scriptable.template.TemplateUtil;
import java.util.List;
import java.util.Set;

@Singleton
public class IfequalDirective implements SoyJsSrcPrintDirective
{
    final static String OPT_DATA = "opt_data.";

    @Inject public IfequalDirective()
    {
    }

    @Override public String getName()
    {
        return "|ifequal";
    }

    @Override public Set<Integer> getValidArgsSizes()
    {
        return ImmutableSet.of(2);
    }

    @Override public boolean shouldCancelAutoescape()
    {
        return true;
    }

    @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args)
    {
        String value = str.getText();
        String fromValue = args.get(0).getText();
        String toValue = args.get(1).getText();

        if (!fromValue.startsWith(OPT_DATA) && !fromValue.startsWith("'"))
            fromValue = "'" + TemplateUtil.escapeJs(fromValue) + "'";

        if (!toValue.startsWith(OPT_DATA) && !toValue.startsWith("'"))
            toValue = "'" + TemplateUtil.escapeJs(toValue) + "'";

        return new JsExpr("((opt_ijData_deprecated=" + value + ")==" + fromValue + "?" + toValue + ":opt_ijData_deprecated)", Integer.MAX_VALUE);
    }
}


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

/**
 * A directive that formats printed number or date as specified by its arguments
 */
@Singleton
public class FmtDateDirective implements SoyJsSrcPrintDirective
{
    final static String OPT_DATA = "opt_data.";

    @Inject public FmtDateDirective()
    {
    }

    @Override public String getName()
    {
        return "|fmtDate";
    }

    @Override public Set<Integer> getValidArgsSizes()
    {
        return ImmutableSet.of(0, 1);
    }

    @Override public boolean shouldCancelAutoescape()
    {
        return true;
    }

    @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args)
    {
        String value = str.getText();
        String fmt = "''";
        if (args.size() > 0)
            fmt = args.get(0).getText();

        if (fmt.startsWith("'") && fmt.endsWith("'"))
            fmt = fmt.substring(1, fmt.length()-1);
        if (!fmt.startsWith(OPT_DATA))
            fmt = "'" + TemplateUtil.escapeJs(fmt) + "'";

        // make sure we pass null (instead of some sort of Undefined object cast to String)
        return new JsExpr("soy.$$formatDate(" + value + "," + fmt + ")",
                Integer.MAX_VALUE);
    }
}


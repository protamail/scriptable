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
public class FmtDirective implements SoyJsSrcPrintDirective
{
    final static String OPT_DATA = "opt_data.";

    @Inject public FmtDirective()
    {
    }

    @Override public String getName()
    {
        return "|fmt";
    }

    @Override public Set<Integer> getValidArgsSizes()
    {
        return ImmutableSet.of(1, 2);
    }

    @Override public boolean shouldCancelAutoescape()
    {
        return true;
    }

    @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args)
    {
        String value = str.getText();
        String type = args.get(0).getText();
        String fmt = "''";
        if (args.size() > 1)
            fmt = args.get(1).getText();

        try {
//            if (!fmt.startsWith("opt_data.")) { // if format string is literal, validate it
//                if (!fmt.startsWith("'") || !fmt.endsWith("'") || fmt.length() < 2)
//                    throw new IllegalArgumentException("Format string must be enclosed in single quotes: " + fmt);
            if (fmt.startsWith("'") && fmt.endsWith("'"))
                TemplateUtil.parseFormat(fmt.substring(1, fmt.length()-1));
//            }
        }
        catch (Exception e) {
//            throw SoySyntaxException.createCausedWithoutMetaInfo(e.getMessage(), e);
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        String fmtString = null;
        if (type.startsWith("'") && type.length() > 1)
            type = type.substring(1, type.length()-1);

        if (type.startsWith(OPT_DATA))
            fmtString = ",'type='+" + type + "+' '+" + fmt + ")";
        else
            fmtString = ",'type=" + type + " '+" + fmt + ")";

        // make sure we pass null (instead of some sort of Undefined object cast to String)
        return new JsExpr("soy.$$format(" + value + fmtString, Integer.MAX_VALUE);
    }
}


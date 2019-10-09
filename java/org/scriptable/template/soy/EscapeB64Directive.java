package org.scriptable.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import java.util.List;
import java.util.Set;
import org.scriptable.util.Base64;

/**
 * A directive that formats printed number or date as specified by its arguments
 */
@Singleton
public class EscapeB64Directive implements SoyJsSrcPrintDirective
{
    final static String OPT_DATA = "opt_data.";

    @Inject public EscapeB64Directive()
    {
    }

    @Override public String getName()
    {
        return "|escapeB64";
    }

    @Override public Set<Integer> getValidArgsSizes()
    {
        return ImmutableSet.of(0);
    }

    @Override public boolean shouldCancelAutoescape()
    {
        return true;
    }

    @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args) {
        String s = str.getText();
        return new JsExpr("soy.$$escapeB64(" + str.getText() + ")", Integer.MAX_VALUE);
    }

}


package org.scriptable.template.soy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import java.util.List;
import java.util.Set;

/**
 * A directive that formats printed number or date as specified by its arguments
 */
@Singleton
public class EvalJsDirective implements SoyJsSrcPrintDirective
{
    final static String OPT_DATA = "opt_data.";

    @Inject public EvalJsDirective()
    {
    }

    @Override public String getName()
    {
        return "|evalJs";
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
        String js = str.getText();

        if (js.startsWith("'") && js.endsWith("'"))
            js = js.substring(1, js.length()-1);

        return new JsExpr("(" + js + ")", Integer.MAX_VALUE);
    }

    static public String dequote(String s)
    {
        char[] characters = s.toCharArray();
        StringBuilder token = new StringBuilder();
        boolean escapeNext = false;
        for(int i = 0; i<characters.length; i++) {
            char ch = characters[i];
            if (escapeNext) {
                escapeNext = false;
                token.append(ch);
            }
            else if (ch == '\\') {
                escapeNext = true;
                continue;
            }
            else if (ch == '\'')
                continue;
            token.append(ch);
        }
        return token.toString();
    }
}


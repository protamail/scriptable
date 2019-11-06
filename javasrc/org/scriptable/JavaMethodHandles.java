package org.scriptable;

/**
 * Speedup access to some of the most often called Java functions from JS
 */

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Context;
import org.scriptable.template.TemplateUtil;
import org.scriptable.template.SoyStringBuilder;
import org.scriptable.template.SoyStringOutput;
import org.scriptable.util.Base64;

public class JavaMethodHandles
{
    static abstract class JsFunction extends BaseFunction {
        public abstract Object call(Object[] args) throws Throwable;

        @Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            try {
                return call(args);
            }
            catch (Throwable e) {
                throw ScriptRuntime.typeError(e.toString());
            }
        }
    };

    public static JsFunction getR() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return ScriptableRequest.getR();
            }
        };
    }

    public static JsFunction getEscapeHtml() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return TemplateUtil.escapeHtml(args[0] == null ? null : args[0].toString());
            }
        };
    }

    public static JsFunction getUnescapeHtml() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return TemplateUtil.unescapeHtml(args[0] == null ? null : args[0].toString());
            }
        };
    }

    public static JsFunction getHtmlFragment() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return new TemplateUtil.HtmlFragment(args[0] == null ? "" : args[0].toString());
            }
        };
    }

    public static JsFunction getEscapeB64() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return Base64.encode(args[0] == null ? null : args[0].toString());
            }
        };
    }

    public static JsFunction getEscapeJs() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return TemplateUtil.escapeJs(args[0] == null ? null : args[0].toString());
            }
        };
    }

    public static JsFunction getUnescapeUnicode() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return TemplateUtil.unescapeUnicode(args[0] == null ? null : args[0].toString());
            }
        };
    }

    public static JsFunction getFormat() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return TemplateUtil.format(args[0], args[1].toString());
            }
        };
    }

    public static JsFunction getFormatAny() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return TemplateUtil.formatAny(args[0], args[1].toString());
            }
        };
    }

    public static JsFunction getFormatDate() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return TemplateUtil.formatDate(args[0], args[1].toString());
            }
        };
    }

    public static JsFunction getApplyJsTemplate() {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return TemplateUtil.applyJsTemplate(args);
            }
        };
    }

    public static JsFunction getSoyStringBuilderAppend(final SoyStringBuilder sb) {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                sb.append(args);
                return null;
            }
        };
    }

    public static JsFunction getSoyStringBuilderToString(final SoyStringBuilder sb) {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return sb.toString();
            }
        };
    }

    public static JsFunction getSoyStringOutputAppend(final SoyStringOutput sb) {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                sb.append(args);
                return null;
            }
        };
    }

    public static JsFunction getSoyStringOutputToString(final SoyStringOutput sb) {
        return new JsFunction() {
            @Override public Object call(Object[] args) throws Throwable {
                return sb.toString();
            }
        };
    }

}


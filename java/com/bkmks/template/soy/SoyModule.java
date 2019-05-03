package com.bkmks.template.soy;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.Module;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.List;
import java.util.ArrayList;

/**
 * Guice module for soy plugin functions
 */
public class SoyModule extends AbstractModule {
    @Override
    public void configure() {
        Multibinder<SoyFunction> soyFunctionsSetBinder =
                Multibinder.newSetBinder(binder(), SoyFunction.class);
        soyFunctionsSetBinder.addBinding().to(ExistsFunction.class);
        soyFunctionsSetBinder.addBinding().to(AppendFunction.class);
        soyFunctionsSetBinder.addBinding().to(IfnullFunction.class);
        soyFunctionsSetBinder.addBinding().to(IfemptyFunction.class);
        soyFunctionsSetBinder.addBinding().to(IfequalFunction.class);
        soyFunctionsSetBinder.addBinding().to(LCFunction.class);
        soyFunctionsSetBinder.addBinding().to(UCFunction.class);
        soyFunctionsSetBinder.addBinding().to(FormatFunction.Number.class);
        soyFunctionsSetBinder.addBinding().to(FormatFunction.Percent.class);
        soyFunctionsSetBinder.addBinding().to(FormatFunction.Currency.class);
        soyFunctionsSetBinder.addBinding().to(FormatFunction.Date.class);
        soyFunctionsSetBinder.addBinding().to(UnescapeFunction.class);
        soyFunctionsSetBinder.addBinding().to(EndsWithFunction.class);

        Multibinder<SoyPrintDirective> soyDirectivesSetBinder =
                Multibinder.newSetBinder(binder(), SoyPrintDirective.class);
        soyDirectivesSetBinder.addBinding().to(FmtDirective.class);
        soyDirectivesSetBinder.addBinding().to(FormatDirective.Decimal.class);
        soyDirectivesSetBinder.addBinding().to(FormatDirective.Percent.class);
        soyDirectivesSetBinder.addBinding().to(FormatDirective.Currency.class);
        soyDirectivesSetBinder.addBinding().to(FormatDirective.Date.class);
        soyDirectivesSetBinder.addBinding().to(FmtDateDirective.class);
        soyDirectivesSetBinder.addBinding().to(IfnullDirective.class);
        soyDirectivesSetBinder.addBinding().to(IfequalDirective.class);
        soyDirectivesSetBinder.addBinding().to(IfemptyDirective.class);
        soyDirectivesSetBinder.addBinding().to(EvalJsDirective.class);
        soyDirectivesSetBinder.addBinding().to(EscapeB64Directive.class);
        soyDirectivesSetBinder.addBinding().to(LCDirective.class);
        soyDirectivesSetBinder.addBinding().to(UCDirective.class);
        soyDirectivesSetBinder.addBinding().to(IdDirective.class);
    }

    /**
     * Include all builtin as well as custom plugin modules here
     */
    public static List<Module> getSoyModules() {
        List<Module> modules = new ArrayList<Module>();
        modules.add(new com.google.template.soy.SoyModule());
        modules.add(new SoyModule());
        return modules;
    }
}
 

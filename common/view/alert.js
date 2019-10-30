function bkAlert(msg, opt = undefined) {
    var restoreFocus = document.activeElement;
    var $al = $(`<alert-shield tabindex="-1" class="alert-anim ${opt && opt.cls || ""}">`)
        .css({
            position: "fixed",
            zIndex: 1000,
            top: 0, left: 0, bottom: 0, right: 0,
            // center content
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            paddingBottom: "18vh" /* shift centered content a little up */
        })
        .html('<alert-body>')
        .on("click keydown", function(e) {

            if (e.target === this && e.type === "click" || e.type === "keydown" && e.which == 27) {
                    $($al[0].firstChild).trigger("close", { originalEvent: e }); // give user code chance to override
            }
            else if (e.type === "click") // window handler expects clicks only
                bkCustomEvents(this, e, 'data-href');

            e.stopPropagation(); // make the dialog modal (blocking)

//            if (e.target === this)
//                $(this).remove();
        })
        .on("close", function(e) {
            $al.remove();

            if (restoreFocus && restoreFocus.focus)
                restoreFocus.focus();
        })
        .appendTo("body");

    var $ab = $al.find("alert-body")
        .css("display", "block");

    if (msg.nodeType)
        $ab.append(msg);
    else
        $ab.html(msg);

    ($al.find("input[autofocus]")[0] || $al[0]).focus();

    // grab the focus async, to override any onload focusing logic
    // (e.g. when called via remoteEval)
//    window.setTimeout(() => {
//        console.log((document.getElementById(opt.focusId)));
//        (opt && opt.focusId? document.getElementById(opt.focusId) : $al[0]).focus();
//    }, 200);

    return $($al[0].firstChild);
}

function bkError(msg, opt) {
    opt = opt || {};
    opt.cls = `error ${opt.cls || ""}`;

    return bkAlert(msg, opt);
}

function bkWarning(msg, opt) {
    opt = opt || {};
    opt.cls = `warning ${opt.cls || ""}`;

    return bkAlert(msg, opt);
}

function bkContextAlert(msg, opt) {
    var $menuElem = $('<menu-body tabindex="-1" data-close-on-any-key class="context-alert">')
        .addClass(opt && opt.cls || "")
        .html(msg)
        .on("menu-body-close", function (e) {
            $menuElem.remove();
        })
        .appendTo("body");

    bkToggleMenu({
        anchorElem: opt.anchorElem,
        menuElem: $menuElem[0],
        pointerStyle: true // display pointer carrot
    });
}

function bkWaitModal() {
    return bkAlert(``, { cls: "wait-modal" })
        .on("close", function (e) {
            if (e.data && e.data.originalEvent)
                e.cancelBubble = true; // don't let user close this
        });
    // bkWaitModal().trigger("close") to clear
}


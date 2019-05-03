// NOTE: keyup will send TAB key to the newly focused element (unlike keydown)
// while keydown is preferable for ENTER and SPACE, since this way default action
// can be prevented for real anchor tags (otherwise those will generate a click)
 
function customClickHandler(e, targetOverride) {
    let updateWindowEvent = !window.event;

    for (let t = targetOverride || e.target; t != null; t = t.parentNode) {
        // interpret links like xxx?a=1&b=2#funcX as funcX("xxx", { a: 1, b: 2 }) if funcX exists
        let hrefs = t.getAttribute && (t.getAttribute("data-href") || t.getAttribute("href"));

        if (hrefs != null) {
            // skip keypress events if not explicitly requested
            if (e.type != "click") {

                if (e.type == "keydown" && t.getAttribute("data-keydown") == null ||
                    e.type == "keyup" && t.getAttribute("data-keyup") == null)
                    continue;
            }

            let $t = $(t);
            let actions = $t.data("href-action");

            if (!actions || actions[0].hrefs != hrefs) {

                for (let href of hrefs.split(" ")) {
                    let h = href.indexOf("#");
                    let f = h != -1? href.substring(h+1) : null;
                    let q = href.indexOf("?");

                    if (f != null && typeof window[f] == "function") {
                        actions = actions || [];
                        actions.push( {
                            f: f,
                            path: href.substring(0, q != -1? q : h != -1? h : href.length),
                            data: q != -1? decodeUrlData(href.substring(q + 1)) : {},
                            hrefs: hrefs // to invalidate cache when href changes
                        } );
                        $t.data("href-action", actions);
                    }
                    else if (f != null)
                        console.warn(f + " is not defined: " + href);
                }
            }

            if (actions) {// && (!(d.data.__click__ || d.data.__enter__ || d.data.__space__) ||
//                        (d.data.__click__ && e.type == "click") ||
//                        (d.data.__enter__ && e.type == "keydown" && e.which == 13) ||
//                        (d.data.__space__ && e.type == "keydown" && e.which == 32))) {

                if (updateWindowEvent)
                    window.event = e; // FF is not setting this one

                for (let a of actions) {
                    let r = window[a.f].call(t, a.path, a.data, e);

                    if (r !== true) // handler can return true to preserve the default link action
                        e.preventDefault(); // don't follow link and prevent subsequent keypress event
                }

                if (e.cancelBubble)
                    break;
            }
            else
                break; // this might an actual href, let it work as usual
        }
    }

    if (updateWindowEvent)
        window.event = null;
}

$(window).on("click", e => {
        if (!e.which || e.which == 1) // e.which is undefined on triggered click
            // by default we handle left click only, nevertheless
            // customClickHandler might be called explicitly for other events as well (e.g. modal dialogs)
            /*e.type == "keydown" && e.which != 13*/
            customClickHandler(e);
    })
    .on("keydown", e => customClickHandler(e))
    .on("keyup", e => customClickHandler(e));

// this function is used to trigger custom events,
//window.trigger = function($this, evName, data) {
//    let e = $.Event(evName);
//    e.data = data; // set data separately to make compatible with jQuery
//    let defaultPrevented = $this.trigger(e) === false;
//    return defaultPrevented;
//}


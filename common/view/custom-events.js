// NOTE: keyup will send TAB key to the newly focused element (unlike keydown)
// while keydown is preferable for ENTER and SPACE, since this way default action
// can be prevented for real anchor tags (otherwise those will generate a click)
 
exports.bkCustomEvents = function (element, e, dataAttr) {
    let updateWindowEvent = !window.event;
    let cont = true; // will turn to false if e.preventPropagation() was called by one of the handlers
    dataAttr = dataAttr || 'data-href';

    for (let c = document.getElementsByTagName("PRE-CUSTOM-EVENT"), i = 0; i < c.length; i++) {
        if (c[i].getAttribute("disabled") == null)
            cont = probeTarget(c[i]) !== false && cont;
    }

    if (cont) {
        for (let t = e.target; t != null; t = t.parentNode) {
            if (probeTarget(t) === false) {
                cont = false;
                break;
            }

            if (t === element) // stop at the element on which bkCustomEvents was called as event handler
                break;
        }
    }

    if (cont) {
        for (let c = document.getElementsByTagName("POST-CUSTOM-EVENT"), i = 0; i < c.length; i++) {
            if (c[i].getAttribute("disabled") == null)
                cont = probeTarget(c[i]) !== false && cont;
        }
    }

    function probeTarget(t) {
        // interpret "links" like xxx?a=1&b=2#funcX as funcX("xxx", { a: 1, b: 2 }) if funcX exists
        // Multiple, space-separated "links" can be specified
        let attr = dataAttr;
        let hrefs = t.getAttribute && t.getAttribute(attr);

        if (!hrefs && t.tagName && t.tagName == "A" && e.type == "click")
            hrefs = t.getAttribute(attr = "href");

        if (hrefs) {
            let bkCache = t.bkCache && t.bkCache[attr];

            if (!bkCache) { // parse if not cached
                t.bkCache = t.bkCache || {};
                bkCache = t.bkCache[attr] = [];

                for (let href of hrefs.split(" ")) {
                    let h = href.indexOf("#");
                    let fn = h != -1? href.substring(h + 1) : null;
                    let q = href.indexOf("?");
                    let func = window[fn];

                    if (fn != null && typeof func == "function") {
                        bkCache.push({
                            func: func,
                            path: href.substring(0, q != -1? q : h != -1? h : href.length),
                            data: q != -1? decodeUrlData(href.substring(q + 1)) : {}
                        });
                    }
                    else if (fn != null)
                        console.warn(fn + " is not defined: " + href);
                }
            }

            if (bkCache) {

                if (updateWindowEvent)
                    window.event = e; // FF is not setting this one

                for (let i = 0; i < bkCache.length; i++) {
                    let c = bkCache[i];
                    let r = c.func.call(t, c.path, c.data, e);

                    if (r !== true) // handler can return true to preserve the default link action
                        e.preventDefault(); // don't follow link and prevent subsequent keypress event
                }

                if (e.cancelBubble)
                    return false;
            }
            else if (t.tagName == "A")
                return false; // this is an actual href, let it work as usual
        }
    }

    if (updateWindowEvent)
        window.event = null;
}

$(window).on("click", e => {
        if (!e.which || e.which == 1) // e.which is undefined on triggered click
//            // by default we handle left click only, nevertheless
//            // bkCustomEvents might be called explicitly at lower levels to handle other events/dataAttr
            exports.bkCustomEvents(window, e, 'data-href');
    })
//    .on("keydown", e => exports.bkCustomEvents(e))
//    .on("keyup", e => exports.bkCustomEvents(e));

// this function is used to trigger custom events,
//window.trigger = function($this, evName, data) {
//    let e = $.Event(evName);
//    e.data = data; // set data separately to make compatible with jQuery
//    let defaultPrevented = $this.trigger(e) === false;
//    return defaultPrevented;
//}


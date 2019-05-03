// apply html template,
// assuming instances of String are valid HTML while everything else needs escaping

function Htm(tmpl) {
    let result = [];

    for (var i = 0; i < tmpl.length - 1;) {

        result.push(tmpl[i++]);
        var v = arguments[i];

        if (v instanceof Array) {

            for (var a of v)
                result.push(a instanceof String? a : a == null? "null" : escapeHtml(a.toString()));
        }
        else
            result.push(v instanceof String? v : v == null? "null" : escapeHtml(v.toString()));
    }

    result.push(tmpl[i]);

    return new String(result.join(""));
}

Htm.html = Htm.js = Htm.id = v => new String(v);

function escapeHtml(v) {

    if (v.indexOf("<") == -1 &&
        v.indexOf('"') == -1 &&
        v.indexOf("&") == -1) // no escaping needed
        return v;

    return v.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

exports.gebid = function (id) {
    return document.getElementById(id);
}

exports.gebcn = function (cn) {
    return document.getElementsByClassName(cn);
}

exports.gebqs = function (qs) {
    return document.querySelectorAll(qs);
}


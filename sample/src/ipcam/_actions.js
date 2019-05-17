const model = require("_model.js");
const view = require("@view.js");

exports["__default__"] = function(r, p, t) {

    t.content = "Not logged in";
    return view.html(t);
}


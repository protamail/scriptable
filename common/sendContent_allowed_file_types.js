exports.sendContent_allowed_file_types = {
    json: {
        contentType: "application/json"
    },
    css: {
        contentType: "text/css"
    },
    less: {
        contentType: "text/css"
    },
    js: {
        contentType: "text/javascript"
    },
    gif: {
        contentType: "image/gif",
        binary: true
    },
    png: {
        contentType: "image/png",
        binary: true
    },
    ico: {
        contentType: "image/x-icon",
        binary: true
    },
    jpg: {
        contentType: "image/jpeg",
        binary: true
    },
    html: {
        contentType: "text/html"
    },
    swf: {
        contentType: "application/x-shockwave-flash",
        binary: true
    },
    htc: {
        contentType: "text/x-component"
    },
    woff: {
        contentType: "font/woff",
        binary: true,
        expiresSec: 30*24*60*60 // expire in 30 days
    },
    woff2: {
        contentType: "font/woff",
        binary: true,
        expiresSec: 30*24*60*60 // expire in 30 days
    },
    ttf: {
        contentType: "font/truetype",
        binary: true,
        expiresSec: 30*24*60*60 // expire in 30 days
    },
    xlsx: {
        contentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        binary: true
    }
};


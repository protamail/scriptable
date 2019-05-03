var term;
$(window).on("load", function() {
    var websocket = new WebSocket((location.protocol == "http:"? "ws://" : "wss://") +
            location.host + contextPath + "/wspty");
    //websocket.binaryType = 'arraybuffer';

    websocket.onerror = function(e) {
        term.write("Connection lost: " + e.code + " " + e.reason);
    };

    websocket.onopen = function(e) {
        term = new Terminal({
            visualBell: true,
            scrollback: 5000,
            cancelEvents: true, // don't let mouse events propagate if handled by app
            middleButtonPaste: true,
            altSelection: true,
            copySelection: true
        });

        var $term = $("#terminal");
        term.open(document.getElementById('terminal'));

        term.on('data', function(str, ev) {
            websocket.send(str);
        });

        term.on('keyfilter', function(result, ev) {
            //log("keyfilter="+ev.keyCode+", key="+result.key);
            switch (ev.keyCode) {
                case 82:
                    if (ev.ctrlKey)
                        location.reload(); // reload on ^r
                    break;
                case 37:
                    if (ev.ctrlKey)
                        result.key = "\x1bOD"; // fix ^Left key code
                    break;
                case 39:
                    if (ev.ctrlKey)
                        result.key = "\x1bOC"; // fix ^Right key code
                    break;
                case 33:
                case 34:
                    if (ev.ctrlKey)
                        result.key = ""; // ignore ^PageUp/Down
                    break;
            }
        });

        term.on('resize', function(e) {
            var str = "resize:" + e.cols + ":" + e.rows;
            var bytes = new Int8Array(str.length);
            for (var i=0; i<str.length; i++)
                bytes[i] = str.charCodeAt(i);
            websocket.send(bytes);
        });

        websocket.onmessage = function(e) {
            term.write(e.data);
        }

        resizeTerm(term, $term);
    }
})
.on("resize", function() {
    resizeTerm(term, $("#terminal"));
});

function resizeTerm(term, $term) {
    var firstRow = term.rowContainer.firstElementChild;
    var saved = firstRow.innerHTML;
    firstRow.style.display = 'inline';
    firstRow.innerHTML = 'W';
    var br = firstRow.getBoundingClientRect();
    var characterWidth = br.width;
    firstRow.style.display = ''; // restore style before calculating height
    var characterHeight = firstRow.offsetHeight;
    characterHeight = br.height;
    firstRow.innerHTML = saved;

    $term.css({
        width: 0,
        height: 0
    });
    var $w = $(window);
    var width = $w.width();
    var height = $w.height();

    var rows = Math.floor((height) / characterHeight);
    var cols = Math.floor((width-5) / characterWidth); // -5 will make sure we don't get wrap around bugs
    term.resize(cols, rows);

    $term.css({
        width: width,
        height: height
    });
}


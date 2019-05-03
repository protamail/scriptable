!function ($, exports) {
    var hideHandlers = [];

    exports.bkHideAllMenus = function () {
        $.each(hideHandlers, function (v) {
            v();
        });
        hideHandlers = [];
    }

    /**
     * data: keepOthers, skipAnimation
     */
    exports.bkToggleMenu = function bkToggleMenu(action, data, e) {
        if (typeof (action) == "object" && data == null)
            data = action;

        var $anchor = data.anchorId != null?
                data.anchorId == "_current"?
                    bkToggleMenu.currentAnchorId? $("#"+bkToggleMenu.currentAnchorId) :
                        bkToggleMenu.$currentAnchor :
                    $("#"+data.anchorId) :
                $(data.anchorElem || data.anchorEl || this);

        if (!$anchor)
            return;

        var menuId = data.menuId || $anchor.data("menu-id");
        var $menu = menuId? $("#"+menuId) :
            data.menuElem || data.menuEl? $(data.menuElem || data.menuEl) :
            data.anchorId == "_current" && bkToggleMenu.$currentMenu? bkToggleMenu.$currentMenu :
            $anchor.next();
        var orientation = (data.orientation || $anchor.data("orientation") || "bottom-right").split(/-/);
        var primaryDir = orientation[0], secondaryDir = orientation[1];

        function hide(opt_e) {
            if (opt_e && $menu.data("close-on-any-key") == null) {
                var $target = $(opt_e.target);

                if (opt_e.type == "keydown") {
                    var $items = $menu.find("menu-item");
                    var $h = $menu.find("menu-item.hover").removeClass("hover");
                    var i = $items.index($h);
                    switch (opt_e.which) {
                        case 13:
                        case 32:
                            opt_e.preventDefault(); // prevent "custom-events" and default anchor action

                            if ($h.length) {
                                $h[0].click();
                                opt_e.stopPropagation(); // hide this from focused anchor
                                return;
                            }

                            break;
                        case 38:
                            opt_e.preventDefault();
                            return ($h.length && i? $items.eq(i-1) : $items.last()).addClass("hover");
                        case 40:
                            opt_e.preventDefault();
                            return ($h.length && i < $items.length-1? $items.eq(i+1) :
                                    $items.first()).addClass("hover");
                    }
                }

                if (opt_e.type == "click" && $target.closest($menu).length || opt_e.type == "keydown" &&
                        opt_e.which != 27 && opt_e.which != 13 && opt_e.which != 32) {
                    return;
                }

                if (opt_e.type == "click" && $target.closest($anchor).length) {
                    opt_e.preventDefault();
                    opt_e.stopPropagation();
                }
            }

            if ($menu.css("display") == "block") {
                (opt_e || window.event || {}).data = { bkMenuClosed: true };

                if (data.onClose)
                    (typeof(data.onClose) == "function"? data.onClose : eval(data.onClose))(false);

                $anchor.removeClass("active");
                $menu.css("display", "none");
                $(document).off("click keydown", hide);

                bkToggleMenu.$currentAnchor = null;
                bkToggleMenu.$currentMenu = null;
                bkToggleMenu.currentAnchorId = null;

//                window.setTimeout(function() { // run async so FF doesn't drop the event when node removed
//                    $menu.trigger($menu[0].tagName.toLowerCase() + "-close")
//                }, 0);
            }
        }

        // make sure we show the menu first so offsetParent works
        var isHidden = $menu.css("display") != "block";
        var skipAnimation = false;

        if (!data.adjustPosition) {

            if (("show" in data) || !("hide" in data) && isHidden) {
                if (!("keepOthers" in data) && isHidden)
                    exports.bkHideAllMenus(); // skip this if menu already displayed, so no flash

                if (!e)
                    e = window.event;
                skipAnimation = e && e.data && e.data.bkMenuClosed;

                // NOTE: window is above document and is the topmost event target
                if (isHidden) {
                    $(document).on("click keydown", hide);
                    hideHandlers.push(hide);
                }

                $anchor.addClass("active");

                if (data.skipAnimation)
                    skipAnimation = true;

                bkToggleMenu.$currentAnchor = $anchor;
                bkToggleMenu.$currentMenu = $menu;
                bkToggleMenu.currentAnchorId = $anchor[0].id;

                if (skipAnimation) { // avoid animation artifacts before we ready to step-start it
                    $menu
                        .css("animation-iteration-count", 0);
                }

                $menu // must follow skipAnimation for smoother action
                    .removeClass("top right bottom left")
                    .addClass(primaryDir)
                    .css("display", "block"); // must display before getting dimensions below
                    //.trigger("menu-open", { $menu: $menu, $anchor: $anchor });

                if (data.onOpen)
                    (typeof(data.onOpen) == "function"? data.onOpen : eval(data.onOpen))(true);
            }
            else
                // don't just call hide() directly as it'll fail to remove document-on-click->hide handler
                return $(document).trigger("click");
        }

        if (data.pointerStyle && $menu.find("svg.pointer").length == 0) {
            //var svg = $('<svg class="pointer"><polygon points="0,0 8,9 16,0"/><path d="M0,0 L8,9 L16,0"/></svg>').css({
            var svg = $('<svg class="pointer"><path d="M12,0 L4,20 L3,10 M4,20 L12,13"/></svg>').css({
                width: "17px",
                height: "20px",
                position: "absolute",
                transformOrigin: "8px 0"
            });
            svg.find("path").css({
                stroke: $menu.css("border-top-color"),
                strokeWidth: $menu.css("border-top-width"),
                strokeLinecap: "round",
                strokeLinejoin: "round",
                fill: "none"
            });
            svg.find("polygon").css({
                fill: $menu.css("background-color")
            });
            svg.appendTo($menu);
        }

        // if menu follows anchor prefer relative positioning otherwise global
        //var aoff = $menu.prev()[0] === $anchor[0]? $anchor.position() : $anchor.offset();
        $menu.css({ position: "absolute", "z-index": 1000, left: 0, top: 0 }); // adjusts for
        var initOffset = $menu.offset();                      // relatively positioned parent(s) if any

        var aoff = $anchor.offset();
        var menuLeft =
            primaryDir == "left"? aoff.left - $menu.outerWidth() :
            primaryDir == "right"? aoff.left + $anchor.outerWidth() :
            secondaryDir == "left"? aoff.left + $anchor.outerWidth() - $menu.outerWidth() :
            secondaryDir == "right"? aoff.left :
                aoff.left + $anchor.outerWidth()/2 - $menu.outerWidth()/2; // default -- center

        var menuTop =
            primaryDir == "bottom"? aoff.top + $anchor.outerHeight() :
            primaryDir == "top"? aoff.top - $menu.outerHeight() :
            secondaryDir == "bottom"? aoff.top :
            secondaryDir == "top"? aoff.top + $anchor.outerHeight() - $menu.outerHeight() :
                aoff.top + $anchor.outerHeight()/2 - $menu.outerHeight()/2; // default -- middle
        if (menuLeft < 0)
            menuLeft = 0;
        if (menuTop < 0)
            menuTop = 0;

        if (data.pointerStyle) {
            var pointerLeft =
                primaryDir == "left"? $menu.innerWidth() -14 :
                primaryDir == "right"? -3 :
                    aoff.left - menuLeft + $anchor.innerWidth()/2 -9;
            var pointerTop =
                primaryDir == "bottom"? 5 :
                primaryDir == "top"? $menu.innerHeight() -5 :
                    aoff.top - menuTop + $anchor.innerHeight()/2 -1;

            // make sure pointer is not outside menu/anchor span
            if ((primaryDir == "bottom" || primaryDir == "top") &&
                    (pointerLeft < 0 || pointerLeft > ($menu.innerWidth() -16)))
                pointerLeft = $menu.innerWidth()/2 -8;
            else if ((primaryDir == "left" || primaryDir == "right") &&
                    (pointerTop < 0 || pointerTop > ($menu.innerHeight() -16)))
                    pointerTop = $menu.innerHeight()/2 -8;

            var rotate =
                primaryDir == "left"? "rotate(270deg)" :
                primaryDir == "right"? "rotate(90deg)" :
                primaryDir == "bottom"? "rotate(180deg)" : "rotate(0deg)";

            $menu.find("svg.pointer").css({
                top: Math.round(pointerTop) + "px",
                left: Math.round(pointerLeft) + "px",
                transform: rotate
            });
        }

        $menu.css({
            left: (menuLeft - initOffset.left) + "px",
            top: (menuTop - initOffset.top) + "px"
        });

        if (data.adjustPosition) // restart animation by triggering reflow
            return;
//            $menu[0].offsetWidth = $menu[0].offsetWidth;

        if (skipAnimation) { // must follow offset parent detection above to not interfere with it
            $menu
                .css("animation-iteration-count", "")
                .css("animation-timing-function", "step-start")
                .on("animationend", function() {
                    $menu.css("animation-timing-function", "")
                });
        }
    }
}($, exports);


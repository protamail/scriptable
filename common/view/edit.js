/**
 * Overlay an element with one/multi-line edit box
 */
// NOTE: we use ! as opposed to - or + prefix, so that it doesn't alter the meaning of any preceeding
// expression not terminated with ;

var bkEdit = (function() {
    var ESC_KEY = 27, ENTER_KEY = 13, TAB_KEY = 9, SPACE_KEY = 32, UP_KEY = 38, DOWN_KEY = 40;
    var $edit_control;
    var input_ctrl, textarea_ctrl;
    var active_index;
    var new_index;
    var original_value;
    var bk_input_sel = "bk-input:not([data-disabled])";
    var _data;

    function cancelEditing() {
        if (isEditing()) {
            var $this = $(bk_input_sel).eq(active_index);

            if ($this.trigger($this[0].tagName.toLowerCase() + "-focusout"))
                return;

            $edit_control.remove();
            $edit_control = null;
        }
        active_index = null;
    }

    function select($this) {
        if (isEditing()) {
            $edit_control[0].select();
        }
    }

    function isEditing() {
        return active_index != null && $edit_control != null &&
            // check if still attached to DOM
            $edit_control.closest("html").length > 0;
    }

    function startEditing(e = undefined) {
        var $l = $(bk_input_sel);
        if (isEditing()) {
            var value = getValue();
            if (value != null && value != original_value) {
                original_value = value; // avoid possible duplicate change events
                var $cur = $l.eq(active_index);
                if ($cur.trigger($cur[0].tagName.toLowerCase() + "-change", { value: value })) {
                    if (e) {
                        e.preventDefault(); // don't follow links or show edit box for next location
                    }
                    return;
                }
            }
            else
                cancelEditing();
        }

        if (input_ctrl == null) {
            var $cur = $l.eq(0);
            $cur.trigger($cur[0].tagName.toLowerCase() + "-event");
            return;
        }

        if (new_index == null || new_index == -1 || active_index == new_index && isEditing())
            return;

        var $this = $l.eq(new_index);

        original_value = _data.value;
        if (original_value == null)
            original_value = $this.data("value");
        if (original_value == null)
            original_value = $this.text();

        var isMl = isMultiline($this);
        var pos = ["absolute", "relative", "fixed"].indexOf($this.css("position")) != -1? // is positioned?
            null : $this.offset();

        $edit_control = (isMl? textarea_ctrl : input_ctrl)
            .remove()
            .css({
                left: (pos? pos.left : 0) + "px",
                top: (pos? pos.top : 0) + "px",
                width: $this.innerWidth() + "px",  // not including
                height: $this.innerHeight() + "px" // margin and borders
            })
            .val(original_value == null? "" : original_value); // make sure undef is not passed

        $edit_control.appendTo($this)[0].focus();

        if (pos) { // in case edit control has offset parents
            var ap = $edit_control.offset();
            if (ap.left != pos.left || ap.top != pos.top) {
                var rp = $edit_control.position();
                $edit_control.css({
                    left: (pos.left + rp.left - ap.left) + "px",
                    top: (pos.top + rp.top - ap.top) + "px"
                });
            }
        }

        if (!isMl)
            $edit_control[0].select();
        active_index = new_index;

        return true;
    }

    function getValue() {
        return $edit_control != null? ($edit_control.val() || "") : null;
    }

    function isMultiline($this) {
        var isMl = $this.data("isMultiline");
        if (isMl == null) {
            isMl = $this.find("bk-src").length > 0 || _data.multiline != null || $this.data("multiline") != null;
            $this.data("isMultiline", isMl);
        }
        return isMl;
    }

    function init() {
        function maskClick(e) {
            if (!e.which || e.which == 1)
                e.stopPropagation();
        }

        input_ctrl = $('<input type=text autocomplete=off value=""></input>')
            .on("click", maskClick);

        textarea_ctrl = $("<textarea></textarea>")
            .attr({ autocorrect: "off" })
            .on("click", maskClick);

        $(document)
            .on("click", function(e) {
                //                if (isEditing()) {
                //                    e.preventDefault(); // avoid following a link when clicked
                //                }
                if (e.which && e.which != 1) // e.which is undefined on triggered click
                    return;
                new_index = null;
                startEditing(e); // just hide edit control when body clicked, possibly generating -change event
            })
            .on("keydown", function(e) {
                if (isEditing() && document.activeElement === $edit_control[0]) {
                    var $l = $(bk_input_sel);
                    var $this = $l.eq(active_index);
                    var key = e.which;

                    if (key == SPACE_KEY || key == ENTER_KEY)
                                             // since the control is attached under it's anchor
                        e.stopPropagation(); // don't let "custom-events" see the activation key

                    if (key == ENTER_KEY && (!isMultiline($this) || !_data.__allowNewline__) ||
                            key == TAB_KEY && (!isMultiline($this) || !_data.__allowTab__) ||
                            (key == DOWN_KEY || key == UP_KEY) && !isMultiline($this)) {

                        // find next cell not excluded from tab order to be edited
                        var cur_tabindex = $l.eq(active_index).attr("tabindex");
                        if (cur_tabindex != -1 && active_index >= 0) {
                            new_index = active_index;
                            var tab_to_index = null; // jump here after finishing with cur_tabindex
                            do {
                                if (key == TAB_KEY && new_index != active_index && tab_to_index == null)
                                    tab_to_index = new_index;

                                new_index = key == UP_KEY || key == TAB_KEY && e.shiftKey?
                                    (new_index-1 >= 0? new_index-1 : tab_to_index != null?
                                        tab_to_index : $l.length-1) :
                                    (new_index+1 < $l.length? new_index+1 : tab_to_index != null?
                                        tab_to_index : 0);

                                if (new_index == tab_to_index)
                                    break;
                            }
                            while (new_index != active_index &&
                                    // make TAB move focus along orthogonal axis
                                    (key == TAB_KEY && $l.eq(new_index).attr("tabindex") == cur_tabindex ||
                                        $l.eq(new_index).attr("tabindex") != cur_tabindex));

                            if (new_index == active_index)
                                new_index = null;
                        }
                        else
                            new_index = null;

                        if (new_index != null) {
                            $l.eq(new_index).trigger("click"); // make sure data-href handler is called
                            e.stopPropagation();
                            e.preventDefault();
                        }
                    }
                    else if (key == ESC_KEY) {
                        if (!$this.trigger($this[0].tagName.toLowerCase() + "-cancel")) {
                            new_index = null;
                            cancelEditing();
                        }
                    }
                }
            });
    }

    var actions = {
        select: function() {
            if (isEditing()) {
                $edit_control[0].select();
            }
        },
        editNext: function() {
            cancelEditing(); // cancel any active editing (discarding changes)
            $(bk_input_sel).eq(new_index).trigger("click"); // make sure data-href handler is called
        },
        cancelEditing: cancelEditing,
        getValue: getValue
    }

    // interface function, returns true if new editing actually started
    return function(action, opt, e) {
        if (input_ctrl == null)
            init();

        if (typeof (action) == "object" && opt == null)
            opt = action;
        _data = opt || {};

        if (actions[action] != null)
            return actions[action]();

        var $this = "elemId" in _data? $("#"+_data.elemId) : "elem" in _data? $(_data.elem) :
            this.tagName? $(this) : $(bk_input_sel).eq(new_index)/* expecting bk_input_sel initialized */;

        if ($this.length)
            bk_input_sel = $this[0].tagName + ":not([data-disabled])[data-href]";
        var $l = $(bk_input_sel);
        new_index = $l.index($this);

        if (new_index == active_index && isEditing()) {
            !e || e.stopPropagation();
        }
        else if (new_index != -1 && (!e || !e.defaultPrevented) &&
                _data["ignore-click"] == null && $this.data("ignore-click") == null) {
            //            !e || e.stopPropagation(); <- do let additional handlers to run
            return startEditing();
        }
    }
})();


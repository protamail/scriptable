/**
 * Create a date object sutable for JDBC
 * @param {Date} date A javascript Date object
 */
var JavaObjectFactory = scriptable.JavaObjectFactory;
exports.date = JavaObjectFactory.createDate;
exports.number = JavaObjectFactory.createDouble;
//exports.arrContext.getCurrentContext().newArray(ScriptableHttpRequest.getGlobalScope(),

exports.nullifyUndef = function(o) {
    // force null value as opposed to "undefined"
    return o == null ? null : o;
}

exports.camelize = scriptable.util.Strings.camelize;

/**
 * Group the Array returned by "all" query by column name(s) and return a new object structured accordingly.
 * @param {(String|Function)[]} colNames array of group by column names or value-producing functions
 *                            (should match one of the selected column names)
 * @param {Boolean} scalarLeaf specifies whether to use scalars to store leafs (only one object per unique
 *                    group key will be set), otherwise an array will be used
 */
function mapBy(queryResults, colNames, scalarLeaf) {
    var result = {}, scope = result;
    var rgroup = colNames.length > 1? new Array(queryResults.length) : null;
    for (var j=0, k=colNames.length; j<k; j++) {
        var colName = colNames[j];
        var colFunc = colName instanceof Function? colName : null;
        var lastLevel = (j == k-1);
        for (var i=0, l=queryResults.length; i<l; i++) {
            var row = queryResults[i];
            if (rgroup != null && rgroup[i] != null)
                scope = rgroup[i];
            var key = colFunc == null? row[colName] : colFunc(i, row);
            if (!(key in scope)) { // new key found
                if (!lastLevel)
                    scope[key] = {};
                else if (!scalarLeaf)
                    scope[key] = [];
            }
            if (lastLevel) {
                if (scalarLeaf)
                    scope[key] = row;
                else
                    scope[key].push(row);
            }
            else
                rgroup[i] = scope[key]; // save row's current level group
        }
    }

    return result;
};

/**
 * Maps rows with pathProp property of the form "a|b|c|" and nameProp being a local name, e.g. "d"
 * to a tree structure reflecting path hierarchy.
 * Note: we assume that (1) rows are sorted by pathProp in ascending order,
 * (2) there are no in-depth level skipping between adjacent paths, (3) path separator is "|",
 * and (4) all paths are terminated with a separator.
 */
function mapByPathAndName(queryResults, pathProp, nameProp) {
    var result = { __parent: null, __children: [], __children_tracker: {} };
    var scope = result;

    for (var i=0, l=queryResults.length; i<l; i++) {
        var row = queryResults[i];
        var path = row[pathProp];

        while (!(path in scope.__children_tracker) && scope.__parent != null)
            scope = scope.__parent;

        if (path in scope.__children_tracker) {
            var parentScope = scope;
            scope = scope.__children_tracker[path];
            if (!("__children" in scope)) {
                scope.__parent = parentScope;
                scope.__children = [];
                scope.__children_tracker = {};
            }
        }
        scope.__children.push(row);
        scope.__children_tracker[path + row[nameProp] + "|"] = row;
    }

    return result;
};

/**
 * @param colNames array or list of column names to nest by,
 *        e.g. ['col1', 'col2', 'col3'] or 'col1', 'col2', 'col3'
 */
function nestBy(queryResults, colNames) {
    var result = [], group = { dict: {}, array: result };
    var rgroup = colNames.length > 1? new Array(queryResults.length) : null;
    for (var j=0,k=colNames.length; j<k; j++) {
        var colName = colNames[j];
        var colFunc = colName instanceof Function? colName : null;
        var lastLevel = (j == k-1);
        for (var i=0, l=queryResults.length; i<l; i++) {
            var row = queryResults[i];
            if (rgroup != null && rgroup[i] != null)
                group = rgroup[i]; // row's group
            var key = colFunc == null? row[colName] : colFunc(i, queryResults);
            var dict = group.dict;
            if (!(key in dict)) { // new key found
                var newgroup = { dict: {}, array: [] };
                dict[key] = newgroup;
                group.array.push(newgroup.array);
            }
            if (lastLevel)
                dict[key].array.push(row);
            else
                rgroup[i] = dict[key]; // update row's group
        }
    }

    return result;
};

/**
 * @param colNames {(String|Function)[]} of column names to sort by, optionally qualified with desc,
 *        e.g. ['col1', 'col2 desc', 'col3']
 */
function sortBy(queryResults, colNames) {
    var desc = [], cols = [];
    for (var i=0,l=colNames.length; i<l; i++) {
        var c = colNames[i].split(/ +/);
        desc.push(c.length > 1 && c[1].toLowerCase().indexOf('desc') == 0? true : false);
        cols.push(c[0]);
    }
    return queryResults.sort(function(a, b) {
        var aa, bb;
        for (var i=0,l=cols.length; i<l; i++) {
            aa = a[cols[i]];
            bb = b[cols[i]];
            if (aa == null)
                aa = '';
            if (bb == null)
                bb = '';
            if (aa < bb)
                return desc[i]? 1 : -1;
            else if (aa > bb)
                return desc[i]? -1 : 1;
        }
        return 0;
    });
};

function toArray(colNames) {
    var cn = colNames;
    if (!(cn instanceof Array)) {
        cn = [];
        for (var i=0,l=arguments.length; i<l; i++)
            cn.push(arguments[i]);
    }
    return cn;
}

function definePrototypeFunc(obj, name, func) {
    Object.defineProperty(obj.prototype, name, {
        value: func,
        enumerable: false, // prevent this from appearing in the "in" list, especially important for Arrays
        writable: true     // let it be redefined
    });
}

definePrototypeFunc(Array, "sortBy", function(colNames) {
    return sortBy(this, toArray(colNames));
});

definePrototypeFunc(Array, "mapBy", function(colNames) {
    return mapBy(this, toArray(colNames), false);
});

definePrototypeFunc(Array, "mapByPathAndName", function(pathProp, nameProp) {
    return mapByPathAndName(this, pathProp, nameProp);
});

definePrototypeFunc(Array, "mapUniqueBy", function(colNames) {
    return mapBy(this, toArray(colNames), true);
});

definePrototypeFunc(Array, "nestBy", function(colNames) {
    return nestBy(this, toArray(colNames));
});

definePrototypeFunc(Array, "groupIndicator", function(colNames, optNewIndColName) {
    return groupIndicator(this, colNames, optNewIndColName);
});

definePrototypeFunc(Array, "groupIndicatorAlt", function(colNames, optNewIndColName) {
    return groupIndicatorAlt(this, colNames, optNewIndColName);
});

definePrototypeFunc(Array, "groupIndicatorLast", function(colNames, optNewIndColName) {
    return groupIndicatorLast(this, colNames, optNewIndColName);
});

/**
 * Convert a column value into a group indicator, that is set it to null for all but the _first_ record in a group
 * of records with matching indicator values
 */
function groupIndicator(queryResults, indColName, optNewIndColName) {
    var lastSeen;
    if (optNewIndColName == null)
        optNewIndColName = indColName;
    for (var i=0, l=queryResults.length; i<l; i++) {
        var row = queryResults[i];
        if (row[indColName] !== lastSeen) {
            lastSeen = row[indColName];
            row[optNewIndColName] = 1;
        }
        else
            row[optNewIndColName] = null;
    }
    return queryResults;
}

/**
 * Convert a column value into an alterbating group indicator, that is set it to null alternating with 1
 * for groups of records with matching indicator values
 */
function groupIndicatorAlt(queryResults, indColName, optNewIndColName) {
    var lastSeen;
    if (optNewIndColName == null)
        optNewIndColName = indColName;
    var groupInd = 1;
    for (var i=0, l=queryResults.length; i<l; i++) {
        var row = queryResults[i];
        if (row[indColName] !== lastSeen) {
            groupInd = groupInd === 1? null : 1;
            lastSeen = row[indColName];
        }
        row[optNewIndColName] = groupInd;
    }
    return queryResults;
}

/**
 * Convert a column value into a group indicator, that is set it to null for all but the _last_ record in a group
 * of records with matching indicator values
 */
function groupIndicatorLast(queryResults, indColName, optNewIndColName) {
    var lastSeen;
    if (optNewIndColName == null)
        optNewIndColName = indColName;

    var row = queryResults[queryResults.length-1];
    row[optNewIndColName] = row[indColName];  

    for (var i=0; i<queryResults.length-1; i++) {
        row = queryResults[i];
        var nextRow = queryResults[i+1];
        if (row[indColName] === nextRow[indColName])
            row[optNewIndColName] = 1;
        else
            row[optNewIndColName] = null;
    }
    return queryResults;
}

exports.defSelectMap = function(ds, query, g1, g2, gx) {
    var select = exports.defSelectAll(ds, query);
    var groupargs = exports.makeArray(arguments);
    groupargs.splice(0, 2);
    return function(params) {
        return select.apply(globalScope, arguments).mapBy(groupargs);
    }
}

exports.defSelectMapUnique = function(ds, query, g1, g2, gx) {
    var select = exports.defSelectAll(ds, query);
    var groupargs = exports.makeArray(arguments);
    groupargs.splice(0, 2);
    return function(params) {
        return select.apply(globalScope, arguments).mapUniqueBy(groupargs);
    }
}

exports.defSelectNest = function(ds, query, g1, g2, gx) {
    var select = exports.defSelectAll(ds, query);
    var groupargs = exports.makeArray(arguments);
    groupargs.splice(0, 2);
    return function(params) {
        return select.apply(globalScope, arguments).nestBy(groupargs);
    }
}

exports.defSelectMapPathAndName = function(ds, query, pathProp, nameProp) {
    var select = exports.defSelectAll(ds, query);
    return function(params) {
        return select.apply(globalScope, arguments).mapByPathAndName(pathProp, nameProp);
    }
}

function findNamedParameters(query) {
    var reColonName = /:\w+/g;
    var named = query.match(reColonName) || [];
    query = query.replace(reColonName, "?");
    for (var i=0; i<named.length; i++)
        named[i] = named[i].substring(1);
    return [query, named];
}

function resolveParams(ds, named, args, justArgs) {
    var param, first = args[0];
    if (exports.isArrayLike(first)) {
        if (first.length == 0)
            throw "Empty batch object was passed";

        if (exports.isArrayLike(first[0]) || first[0] instanceof Object) { // this is a batch update
            var r0 = [];
            for (var i=0; i<first.length; i++) {
                r0.push(resolveParams(ds, named, [first[i]], true));
            }
            param = [r0];
        }
        else { // non-batch parameters were passed as an array, or recursive batch array
            param = first;
        }
    }
    else if (first instanceof Object) {
        // assume we have named parameter object
        param = [];
        for (var i=0; i<named.length; i++) {
            if (!(named[i] in first))
                throw "Missing required database query parameter: " + named[i];
            param.push(first[named[i]]);
        }
    }
    else {
        param = exports.makeArray(args);
    }

    for (var i=0; i<param.length; i++) {
        // since Undefined values are passed to java as "undefined" strings,
        // convert them to nulls for consistency
        if (param[i] == null)
            param[i] = null
    }

    if (justArgs) // short version for batch processing
        return param;

    var connection = Jdbc(ds, false /* do produce camelcase */, true /* keep conn alive */);

    return [param, connection];
}

function getConn(ds, tmplArr, args) {

    if (tmplArr._sql_stmt == null)
        tmplArr._sql_stmt = tmplArr.join("?").trim();

    args[0] = tmplArr._sql_stmt;

    for (var i = 1; i < args.length; i++)
        if (args[i] == null)
            args[i] = null; // convert any Undefined values to null

    return Jdbc(ds, false /* do produce camelcase */, true /* keep conn alive */);
}

exports.sql = function(ds) {

    return {
        all: function(tmplArr, params) {

            var c = getConn(ds, tmplArr, arguments);

            return c.all.apply(c, arguments);
        },

        one: function(tmplArr, params) {

            var c = getConn(ds, tmplArr, arguments);

            return c.one.apply(c, arguments);
        },

        call: function(tmplArr, params) {

            var c = getConn(ds, tmplArr, arguments);

            return c.call.apply(c, arguments);
        },

        update: function(tmplArr, params) {

            var c = getConn(ds, tmplArr, arguments);

            return c.update.apply(c, arguments);
        },

        val: function(tmplArr, params) {

            var c = getConn(ds, tmplArr, arguments);
            var r = c.one.apply(c, arguments);

            return r[Object.keys(r)[0]];
        }
    }
}

exports.defSelectAll = function(ds, query) {
    var named;
    [query, named] = findNamedParameters(query);
    return function(params) {
        var [args, c] = resolveParams(ds, named, arguments);
        args.unshift(query);
        return c.all.apply(c, args);
    }
}

exports.defSelectOne = function(ds, query) {
    var named;
    [query, named] = findNamedParameters(query);
    return function(params) {
        var [args, c] = resolveParams(ds, named, arguments);
        args.unshift(query);
        return c.one.apply(c, args);
    }
}

/**
 * Returns the value of the first row/column returned by query
 */
exports.defSelectVal = function(ds, query) {
    var named;
    [query, named] = findNamedParameters(query);
    return function(params) {
        var [args, c] = resolveParams(ds, named, arguments);
        args.unshift(query);
        var r = c.one.apply(c, args);
        return r[Object.keys(r)[0]];
    }
}

exports.defCall = function(ds, query) {
    var named;
    [query, named] = findNamedParameters(query);
    return function(params) {
        var [args, c] = resolveParams(ds, named, arguments);
        args.unshift(query);
        return c.call.apply(c, args);
    }
}

exports.defUpdate = function(ds, query) {
    var named;
    [query, named] = findNamedParameters(query);
    return function(params) {
        var [args, c] = resolveParams(ds, named, arguments);
        args.unshift(query);
        return c.update.apply(c, args);
    }
}


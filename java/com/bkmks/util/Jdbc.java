package com.bkmks.util;

/**
 * Access JDBC database, return results suitable for javascript consumption
 */

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.CallableStatement;
import java.sql.Types;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ParameterMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.locks.Lock;
import com.bkmks.RhinoHttpRequest;
import com.bkmks.ScriptableMap;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.NativeArray;

import java.sql.SQLException;
import java.io.IOException;
import com.bkmks.ValidationException;

public final class Jdbc
{
    private Connection _conn = null;
    private boolean keepConnAlive = false;
    private boolean camelize = false;
    private JdbcDataSourceConfig dsConfig = null;
    private RhinoHttpRequest rhr = RhinoHttpRequest.getCurrentRequest();
    HashMap<JdbcDataSourceConfig, Connection> ccached = null;
    private final static ThreadLocal<HashMap<JdbcDataSourceConfig, Connection>> ccache =
        new ThreadLocal<HashMap<JdbcDataSourceConfig, Connection>>();

    static {
        /**
         * Close and clear all cached connections for the current task/request
         */
        RhinoHttpRequest.threadLocalPerTaskCleaner.register(()-> {
            HashMap<JdbcDataSourceConfig, Connection> ccached = ccache.get();

            if (ccached != null) {

                for (Connection conn: ccached.values()) {

                    try {

                        if (!conn.getAutoCommit())
                            conn.rollback(); // rollback any uncommitted changes to prevent table locking

                        if (!conn.isClosed())
                            conn.close();
                    }
                    catch (Exception e) {
                    }
                }

                ccached.clear();
            }
        });
    }

    public Jdbc(JdbcDataSourceConfig ds) throws SQLException {
        this(ds, false, false);
    }

    public Jdbc(JdbcDataSourceConfig ds, boolean noCamelCase) throws SQLException {
        this(ds, noCamelCase, false);
    }

    // batch possibly costly operation with (JS-unlocked) query block
    private Connection getConnection() throws SQLException {

        if (_conn == null) {
            ccached = ccache.get();

            if (ccached == null) {
                ccached = new HashMap<JdbcDataSourceConfig, Connection>();
                ccache.set(ccached);
            }

            _conn = ccached.get(dsConfig);

            if (_conn == null) {
                startTimer();
                _conn = dsConfig.datasource.getConnection();
                _conn.setAutoCommit(true);

                if (keepConnAlive)
                    ccached.put(dsConfig, _conn);

                reportTimer("getting connection ...");
            }
        }

        return _conn;
    }

    public Jdbc(JdbcDataSourceConfig ds, boolean noCamelCase, boolean keepOpen) throws SQLException {
        dsConfig = ds;
        keepConnAlive = keepOpen;
        camelize = !noCamelCase;
    }

    public void setKeepConnectionAlive(boolean v) {
        if (v && _conn != null && ccached.get(dsConfig) == null)
            ccached.put(dsConfig, _conn);
        keepConnAlive = v;
    }

    public static Jdbc begin(JdbcDataSourceConfig ds) throws SQLException {
        return new Jdbc(ds).begin();
    }

    public Jdbc begin() throws SQLException {
        Connection conn = getConnection();
        if (conn.isClosed())
            throw new SQLException("This connection is already closed");
        setKeepConnectionAlive(true);
        conn.setAutoCommit(false);
        return this;
    }

    public void commit() throws SQLException {
        Connection conn = getConnection();
        conn.commit();
    }

    public void rollback() throws SQLException {
        Connection conn = getConnection();
        if (!keepConnAlive)
            throw new SQLException("No transaction is in progress");
        conn.rollback();
    }

    public void end() throws SQLException {
        Connection conn = getConnection();
        commit();
        conn.setAutoCommit(true);
    }

    public void close() throws SQLException {
        Connection conn = getConnection();
        ccached.remove(dsConfig);
        if (!conn.getAutoCommit())
            conn.rollback(); // important when batch-updating, or table may be left locked
        if (!conn.isClosed())
            conn.close();
    }

    static Boolean parameterTypeMetaSupported = null;
    static final boolean isParameterTypeMetaSupported(ParameterMetaData pmd) {
        if (parameterTypeMetaSupported == null) {
            try {
                if (pmd.getParameterCount() < 1)
                    return false;
                pmd.getParameterType(1);
                parameterTypeMetaSupported = true;
            }
            catch (Exception e) {
                parameterTypeMetaSupported = false;
            }
        }
        return parameterTypeMetaSupported;
    }

    // returns true if this is a batch request
    private boolean setParams(int startIdx, PreparedStatement c, Object... params) throws SQLException {
        if (params == null) // received single parameter value of null
            c.setObject(startIdx, params);
        else {
            ParameterMetaData pmd = c.getParameterMetaData();
            if (params.length > 0 && params[0] instanceof List) { // this is batch request
                for (Object b: params) {
                    int i = startIdx;
                    for (Object p: (List)b) {
                        if (p != null && isParameterTypeMetaSupported(pmd)) {
                            switch (pmd.getParameterType(i)) {
                                case Types.INTEGER:
                                    p = ((Number)p).intValue();
                                    break;
                                case Types.BIGINT:
                                    p = ((Number)p).longValue();
                                    break;
                            }
                        }
                        c.setObject(i++, p);
                    }
                    c.addBatch();
                }
                return true;
            }
            else { // this is regular request
                for (Object p: params) {
                    if (p != null && isParameterTypeMetaSupported(pmd)) {
                        switch (pmd.getParameterType(startIdx)) {
                            case Types.INTEGER:
                                p = ((Number)p).intValue();
                                break;
                            case Types.BIGINT:
                                p = ((Number)p).longValue();
                                break;
                        }
                    }
                    c.setObject(startIdx++, p);
                }
            }
        }
        return false;
    }

    private NativeArray emptyList = null;

    @SuppressWarnings("unchecked")
    NativeArray getEmptyList() {

        if (emptyList == null)
            emptyList = (NativeArray)Context.getCurrentContext().newArray(RhinoHttpRequest.getGlobalScope(),
                        new ScriptableMap[0]);

        return emptyList;
    }

    public static final int
        STRING = 1,
        DOUBLE = 2,
        OBJECT = 3,
        TIME = 4,
        BYTE_ARRAY = 5,
        BLOB = 6,
        CLOB = 7;

    static final class ColInfo {
        LinkedHashMap<String, Integer> keys;
        int[] types;
    }

    static ColInfo getColInfo(ResultSet r) throws SQLException {
        ResultSetMetaData m = r.getMetaData();
        int ncol = m.getColumnCount();

        ColInfo ci = new ColInfo();
        ci.keys = new LinkedHashMap<String, Integer>(ncol*2);
        ci.types = new int[ncol];

        for (int i = 0; i < ncol; i++) {
            int type;

            switch (m.getColumnType(i + 1)) {
                case Types.VARCHAR:
                case Types.CHAR:
                case Types.CLOB:
                    type = STRING;
                    break;
                case Types.DOUBLE:
                case Types.FLOAT:
//                case Types.INTEGER: <- let these be extracted as integer types in default case
//                case Types.BIGINT:
//                case Types.TINYINT:
//                case Types.DECIMAL:
//                case Types.REAL:
                    type = DOUBLE;
                    break;
                case Types.DATE:
                case Types.TIME:
                case Types.TIME_WITH_TIMEZONE:
                case Types.TIMESTAMP:
                case Types.TIMESTAMP_WITH_TIMEZONE:
                    type = TIME;
                    break;
                case Types.BINARY:
                    type = BYTE_ARRAY;
                    break;
//                case Types.CLOB:
//                    type = CLOB;
//                    break;
//                case Types.BLOB:
//                    type = BLOB;
//                    break;
                //case Types.ARRAY:
                default:
                    type = OBJECT;
            }

            ci.types[i] = type;

            // intern helps to speedup property/map access since JS internalizes its tokens/symbols,
            // so key lookup ends up using '==' rather than 'equal' for comparizon
            ci.keys.put(Strings.camelize(m.getColumnName(i + 1)).intern(), i);
        }

        return ci;
    }

    public static Long getTimeLong(ResultSet r, int colIndex) throws SQLException {
        Timestamp d = r.getTimestamp(colIndex);
        return d != null ? d.getTime() : null;
    }

    public NativeArray all(String sql, Object... params)
        throws SQLException, ValidationException, InterruptedException {

        return select(sql, false, params);
    }

    /**
     * Note: Date values are returned as number of millis since epoch
     */
    @SuppressWarnings("unchecked")
    public NativeArray select(String sql, boolean atLeastOne, Object... params)
        throws SQLException, ValidationException, InterruptedException {
        PreparedStatement stmt = null;
        ResultSet r = null;

        try {
            rhr.unlockJS(); // let async query to run concurrently with js code
            startTimer();
            Connection conn = getConnection();
            stmt = conn.prepareStatement(sql);
            setParams(1, stmt, params);
            r = stmt.executeQuery();

            if (r == null)
                return getEmptyList();

            r.setFetchSize(128); // speed up retrieval

            ColInfo ci = getColInfo(r);
            ArrayList<ScriptableMap> root = new ArrayList<ScriptableMap>(128);
            int ncol = ci.types.length;

            while (r.next()) {
                Object[] row = new Object[ncol];

                for (int i = 0; i < ncol; i++) {

                    switch (ci.types[i]) {
                        case STRING:
                            row[i] = r.getString(i + 1);
                            break;
                        case DOUBLE:
                            row[i] = r.getDouble(i + 1);
                            break;
                        case TIME:
                            row[i] = getTimeLong(r, i + 1);
                            break;
                        case BYTE_ARRAY:
                            row[i] = r.getBytes(i + 1);
                            break;
/*                        case CLOB:
                            Clob clob = r.getClob(i+1);
                            if (callback != null)
                                row.put(key, clob); // let callback interface Clob directly
                            else {
                                row.put(key, Files.getReaderAsString(clob.getCharacterStream()));
                                clob.free();
                            }
                            break;
                        case BLOB:
                            Blob blob = r.getBlob(i+1);
                            if (callback != null)
                                row.put(key, blob); // let callback interface Blob directly
                            else {
                                row.put(key, Files.getStreamAsBytes(blob.getBinaryStream()));
                                blob.free();
                            }
                            break;*/
                        case OBJECT:
                            row[i] = r.getObject(i + 1);
                            break;
                        default:
                            throw new RuntimeException("Unrecognized field type parameter.");
                    }
                }

                root.add(new ScriptableMap(ci.keys, row, true));
            }

            // return at least one row, even if with all null values
            if (atLeastOne && root.size() == 0)
                root.add(new ScriptableMap(ci.keys, new Object[ncol], true));

            return (NativeArray)
                Context.getCurrentContext().newArray(RhinoHttpRequest.getGlobalScope(), root.toArray());
        }
        catch (SQLException ie) {

            if (dsConfig.isValidationError(ie.getErrorCode()))
                throw new ValidationException(ie.getMessage());

            augmentAndRethrow(ie, sql, params);
        }
        finally {

            try {
                r.close();
            }
            catch (Exception e) {
            }

            try {
                stmt.close();
            }
            catch (Exception e) {
            }

            if (!keepConnAlive) {

                try {
                    close();
                }
                catch (Exception e) {
                }
            }

            rhr.lockJS(); // restore js lock before returning
            reportTimer(sql);
        }

        return null;
    }

    // return exactly one row
    public ScriptableMap one(String sql, Object... params)
        throws SQLException, ValidationException, InterruptedException {

        // return at least one row, even if with all null values
        NativeArray result = select(sql, true, params);
        ScriptableMap r = null;

        if (result.size() > 1)
            throw new ValidationException("one: selected multiple rows while expected at most one");

        return (ScriptableMap) result.get(0);
    }

    public Object update(String sql, Object... params)
        throws SQLException, ValidationException, InterruptedException {

        PreparedStatement stmt = null;
        Object rows_affected = null;

        try {
            rhr.unlockJS(); // let async query to run concurrently with js code
            startTimer();
            Connection conn = getConnection();
            stmt = conn.prepareStatement(sql);

            boolean isBatch = setParams(1, stmt, params);
            if (isBatch) {
                boolean autoCommitOn = conn.getAutoCommit();
                conn.setAutoCommit(false);
                rows_affected = stmt.executeBatch();
                if (autoCommitOn) {
                    conn.commit();
                    conn.setAutoCommit(true);
                }
            }
            else
                rows_affected = Integer.valueOf(stmt.executeUpdate());
            // don't commit, since auto commit is enabled
        }
        catch (SQLException ie) {
            if (dsConfig.isValidationError(ie.getErrorCode()))
                throw new ValidationException(ie.getMessage());
            augmentAndRethrow(ie, sql, params);
        }
        finally {
            try {
                stmt.close();
            } catch (Exception e) {}

            if (!keepConnAlive) {
                try {
                    close();
                } catch (Exception e) {}
            }
            rhr.lockJS(); // restore js lock before returning
            reportTimer(sql);
        }
        return rows_affected;
    }

    /**
     * Call a stored procedure or function
     */
    public Object call(String sql, Object... params)
        throws SQLException, ValidationException, InterruptedException {
        CallableStatement stmt = null;

        try {
            rhr.unlockJS(); // let async query to run concurrently with js code
            startTimer();
            Connection conn = getConnection();
            stmt = conn.prepareCall(sql);

            if (sql.startsWith("{?")) { // handle function call statements {? = call...} (can't be batched)
                stmt.registerOutParameter(1, Types.VARCHAR);
                setParams(2, stmt, params);
                stmt.execute();
                return stmt.getObject(1);
            }

            boolean isBatch = setParams(1, stmt, params);
            if (isBatch) {
                boolean autoCommitOn = conn.getAutoCommit();
                conn.setAutoCommit(false);
                Object rows_affected = stmt.executeBatch();
                if (autoCommitOn) {
                    conn.commit();
                    conn.setAutoCommit(true);
                }
                return rows_affected;
            }
            else {
                stmt.execute();
            }
        }
        catch (SQLException ie) {
            if (dsConfig.isValidationError(ie.getErrorCode()))
                throw new ValidationException(ie.getMessage());
            augmentAndRethrow(ie, sql, params);
        }
        finally {
            try {
                stmt.close();
            } catch (Exception e) {}

            if (!keepConnAlive) {
                try {
                    close();
                } catch (Exception e) {}
            }
            rhr.lockJS(); // restore js lock before returning
            reportTimer(sql);
        }

        return null;
    }

    /**
     * Calls JS error handler and returns null if handled successfully or an exception otherwise
     */
/*    public Exception handleError(Function errorHandler, Exception ex) throws InterruptedException {
        try {
            rhr.lockJS();
            RhinoHttpRequest.callJsFunction(errorHandler, ex);
            return null;
        }
        catch (Exception e) {
            return e;
        }
        finally {
            rhr.unlockJS();
        }
    }*/

    long beginTS = 0;
    final private void startTimer() {
        if (RhinoHttpRequest.isDevelopmentMode()) {
            beginTS = System.currentTimeMillis();
        }
    }

    final private void reportTimer(String sql) {
        if (RhinoHttpRequest.isDevelopmentMode()) {
            RhinoHttpRequest.logInfo((System.currentTimeMillis() - beginTS) + "ms for: " + sql);
        }
    }

    final private void augmentAndRethrow(SQLException ie, String sql, Object... params)
        throws SQLException {
        String msg = ie.getMessage();
        ArrayList<String> ps = new ArrayList<String>();
        if (params != null) {
            for (Object p: params) {
                String pp = p + ""; // handles null case
                ps.add(pp.length() > 20? pp.substring(0, 20) + "..." : pp);
            }
        }
        sql += " [args: " + String.join(", ", ps.toArray(new String[ps.size()])) + "]";
        // don't init cause since error reporting unwraps
        throw (SQLException)new SQLException(ie.getClass().getName() + ": " + msg +
                (msg.charAt(msg.length()-1) == '\n'? "SQL: " : "\nSQL: ") + sql,
                ie.getSQLState(), ie.getErrorCode()).initCause(ie);
    }
}


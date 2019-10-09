package org.scriptable.util;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.naming.NamingException;

public class JdbcDataSourceConfig
{
    public final DataSource datasource;
    public final Integer[] validationErrorCodes;

    public JdbcDataSourceConfig(String dsName, Integer... validationErrorCodes)
        throws NamingException {
        this.datasource = lookupDatasource(dsName);
        this.validationErrorCodes = validationErrorCodes;
    }

    public boolean isValidationError(int errorCode) {
        for (int e: validationErrorCodes)
            if (e == errorCode)
                return true;
        return false;
    }

    public static DataSource lookupDatasource(String datasource)
        throws NamingException {
        return (DataSource) new InitialContext().lookup("java:/comp/env/jdbc/" + datasource);
    }
}


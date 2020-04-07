package ai.libs.sqlrest;

import ai.libs.jaicore.db.IDatabaseAdapter;

import java.sql.SQLException;

public interface ISQLAdapterAccess {

    IDatabaseAdapter acquire(String token) throws SQLException, InterruptedException;

    void release(IDatabaseAdapter adapter, String token) throws SQLException;

}

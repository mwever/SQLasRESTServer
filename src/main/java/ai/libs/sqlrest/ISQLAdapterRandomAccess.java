package ai.libs.sqlrest;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.jaicore.db.sql.SQLAdapter;

import java.sql.SQLException;
import java.util.List;

public class ISQLAdapterRandomAccess implements ISQLAdapterAccess {

    private final SQLAdapterManager adapterManager;

    public ISQLAdapterRandomAccess(SQLAdapterManager adapterManager) {
        this.adapterManager = adapterManager;
    }

    @Override
    public IDatabaseAdapter acquire(String token) throws SQLException {
        List<IDatabaseAdapter> adapters = adapterManager.getAdaptersFor(token);
        int size = adapters.size();
        int randomIndex = (int) (Math.random() * size);
        return adapters.get(randomIndex);
    }

    @Override
    public void release(IDatabaseAdapter adapter, String token) throws SQLException {

    }

}

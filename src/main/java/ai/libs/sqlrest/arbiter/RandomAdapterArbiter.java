package ai.libs.sqlrest.arbiter;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.sqlrest.IAdapterArbiter;
import ai.libs.sqlrest.SQLAdapterManager;

import java.sql.SQLException;
import java.util.List;

public class RandomAdapterArbiter implements IAdapterArbiter {

    private final SQLAdapterManager adapterManager;

    public RandomAdapterArbiter(SQLAdapterManager adapterManager) {
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

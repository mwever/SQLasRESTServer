package ai.libs.sqlrest.interceptors;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.sqlrest.ClosableQuery;
import ai.libs.sqlrest.IQueryInterceptor;
import ai.libs.sqlrest.SQLAdapterManager;
import ai.libs.sqlrest.model.SQLQuery;

import java.sql.SQLException;
import java.util.List;

public class RandomConnectionArbiter implements IQueryInterceptor {

    private final SQLAdapterManager adapterManager;

    public RandomConnectionArbiter(SQLAdapterManager adapterManager) {
        this.adapterManager = adapterManager;
    }

    @Override
    public ClosableQuery requestConnection(SQLQuery query) throws SQLException {
        String token = query.getToken();
        List<IDatabaseAdapter> adapters = adapterManager.getAdaptersFor(token);
        int size = adapters.size();
        int randomIndex = (int) (Math.random() * size);
        IDatabaseAdapter adapter = adapters.get(randomIndex);
        return new ClosableQuery(adapter, query);
    }


}

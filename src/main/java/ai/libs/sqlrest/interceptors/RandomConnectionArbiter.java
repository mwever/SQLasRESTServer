package ai.libs.sqlrest.interceptors;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.sqlrest.ClosableQuery;
import ai.libs.sqlrest.IQueryInterceptor;
import ai.libs.sqlrest.SQLAdapterManager;
import ai.libs.sqlrest.model.SQLQuery;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

public class RandomConnectionArbiter implements IQueryInterceptor {

    private final Function<String, List<IDatabaseAdapter>> adapterManager;

    private final Function<Integer, Integer> indexPicker;

    public RandomConnectionArbiter(SQLAdapterManager adapterManager) {
        this.adapterManager = adapterManager::getAdaptersFor;
        this.indexPicker = size -> (int) (Math.random() * size); // by default a random is taken
    }

    // For testing
    RandomConnectionArbiter(Function<String, List<IDatabaseAdapter>> adapterManager,
                                   Function<Integer, Integer> indexPicker) {
        this.adapterManager = adapterManager;
        this.indexPicker = indexPicker;
    }

    @Override
    public ClosableQuery requestConnection(SQLQuery query) throws SQLException {
        String token = query.getToken();
        List<IDatabaseAdapter> adapters = adapterManager.apply(token);
        int size = adapters.size();
        int randomIndex = indexPicker.apply(size);
        IDatabaseAdapter adapter = adapters.get(randomIndex);
        return new ClosableQuery(adapter, query);
    }


}

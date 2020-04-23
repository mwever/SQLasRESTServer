package ai.libs.sqlrest.interceptors;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.sqlrest.ClosableQuery;
import ai.libs.sqlrest.IQueryInterceptor;
import ai.libs.sqlrest.SQLAdapterManager;
import ai.libs.sqlrest.model.SQLQuery;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CyclicConnectionArbiter implements IQueryInterceptor {

    private final Map<String, AtomicInteger> tokenSQLAdapterIndexMap = new ConcurrentHashMap<>();

    private final SQLAdapterManager adapterManager;

    public CyclicConnectionArbiter(SQLAdapterManager adapterManager) {
        this.adapterManager = adapterManager;
    }

    @Override
    public ClosableQuery requestConnection(SQLQuery query) throws SQLException {
        String token = query.getToken();
        AtomicInteger atomicIndex
                = tokenSQLAdapterIndexMap.computeIfAbsent(token, t -> new AtomicInteger(0));
        int currentIndex = atomicIndex.getAndIncrement();
        int readIndex = currentIndex;
        List<IDatabaseAdapter> adapters = adapterManager.getAdaptersFor(token);
        int numAdapters = adapters.size();
        if(currentIndex < 0) {
            throw new IllegalStateException("The current index is negative: " + currentIndex);
        }
        while(currentIndex >= adapters.size()) {
            currentIndex = currentIndex % adapters.size();
        }
        atomicIndex.compareAndSet(readIndex, currentIndex + 1);
        IDatabaseAdapter adapter = adapters.get(currentIndex);
        ClosableQuery access = new ClosableQuery(adapter, query);
        return access;
    }

}

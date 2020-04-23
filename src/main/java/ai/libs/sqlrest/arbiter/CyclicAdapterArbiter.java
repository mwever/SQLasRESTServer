package ai.libs.sqlrest.arbiter;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.sqlrest.IAdapterArbiter;
import ai.libs.sqlrest.SQLAdapterManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CyclicAdapterArbiter implements IAdapterArbiter {

    private final Map<String, AtomicInteger> tokenSQLAdapterIndexMap = new ConcurrentHashMap<>();

    private final SQLAdapterManager adapterManager;

    public CyclicAdapterArbiter(SQLAdapterManager adapterManager) {
        this.adapterManager = adapterManager;
    }

    @Override
    public IDatabaseAdapter acquire(String token) throws SQLException {
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
        return adapters.get(currentIndex);
    }

    @Override
    public void release(IDatabaseAdapter adapter, String token) {
        // releasing doesn't do anything
    }
}

package ai.libs.sqlrest.arbiter;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.sqlrest.IAdapterArbiter;
import ai.libs.sqlrest.IServerConfig;
import org.aeonbits.owner.ConfigCache;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class LimitedAccessAdapterArbiter implements IAdapterArbiter {

    private final IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);

    private Map<String, Semaphore> tokenPermitsMap;

    private IAdapterArbiter arbiter;

    public LimitedAccessAdapterArbiter(IAdapterArbiter arbiter) {
        this.arbiter = arbiter;
        this.tokenPermitsMap = new ConcurrentHashMap<>();
    }

    @Override
    public IDatabaseAdapter acquire(String token) throws SQLException, InterruptedException {
        Semaphore permits = tokenPermitsMap.computeIfAbsent(token,
                t -> new Semaphore(config.getNumAdapterAccessLimit()));
        permits.acquire();
        return arbiter.acquire(token);
    }

    @Override
    public void release(IDatabaseAdapter adapter, String token) throws SQLException {
        Semaphore permits = tokenPermitsMap.get(token);
        if(permits == null) {
            throw new IllegalStateException("Permits wasn't initialized for token, but adapter is released.");
        }
        permits.release();
    }
}

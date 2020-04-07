package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.aeonbits.owner.ConfigCache;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class ISQLAdapterLimitedAccess implements ISQLAdapterAccess {

    private final IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);

    private Map<String, Semaphore> tokenPermitsMap;

    private ISQLAdapterAccess adapterAccess;

    public ISQLAdapterLimitedAccess(ISQLAdapterAccess adapterAccess) {
        this.adapterAccess = adapterAccess;
        this.tokenPermitsMap = new ConcurrentHashMap<>();
    }

    @Override
    public SQLAdapter acquire(String token) throws SQLException, InterruptedException {
        Semaphore permits = tokenPermitsMap.computeIfAbsent(token,
                t -> new Semaphore(config.getNumAdapterAccessLimit()));
        permits.acquire();
        return adapterAccess.acquire(token);
    }

    @Override
    public void release(SQLAdapter adapter, String token) throws SQLException {
        Semaphore permits = tokenPermitsMap.get(token);
        if(permits == null) {
            throw new IllegalStateException("Permits wasn't initialized for token, but adapter is released.");
        }
        permits.release();
    }
}

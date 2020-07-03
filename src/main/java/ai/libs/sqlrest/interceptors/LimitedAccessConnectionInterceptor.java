package ai.libs.sqlrest.interceptors;

import ai.libs.sqlrest.ClosableQuery;
import ai.libs.sqlrest.IQueryInterceptor;
import ai.libs.sqlrest.IServerConfig;
import ai.libs.sqlrest.model.SQLQuery;
import org.aeonbits.owner.ConfigCache;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class LimitedAccessConnectionInterceptor implements IQueryInterceptor {

    private final IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);

    private Map<String, Semaphore> tokenPermitsMap;

    private IQueryInterceptor arbiter;

    public LimitedAccessConnectionInterceptor(IQueryInterceptor arbiter) {
        this.arbiter = arbiter;
        this.tokenPermitsMap = new ConcurrentHashMap<>();
    }

    @Override
    public ClosableQuery requestConnection(SQLQuery query) throws SQLException, InterruptedException {
        String token = query.getToken();
        Semaphore permits = tokenPermitsMap.computeIfAbsent(token,
                t -> new Semaphore(config.getNumAdapterAccessLimit()));
        permits.acquire();
        ClosableQuery access = arbiter.requestConnection(query);
        access.addCloseHook(this::release);
        return access;
    }

    private void release(ClosableQuery closableQuery) {
        Semaphore permits = tokenPermitsMap.get(closableQuery.getQuery().getToken());
        if(permits == null) {
            throw new IllegalStateException("Permits wasn't initialized for token, but adapter is released.");
        }
        permits.release();
    }

}

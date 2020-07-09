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

    private final int numAccessLimit;

    private final Map<String, Semaphore> tokenPermitsMap = new ConcurrentHashMap<>();

    private final IQueryInterceptor arbiter;

    public LimitedAccessConnectionInterceptor(IQueryInterceptor arbiter) {
        this.numAccessLimit = ConfigCache.getOrCreate(IServerConfig.class).getNumAdapterAccessLimit();
        this.arbiter = arbiter;
    }


    public LimitedAccessConnectionInterceptor(int numAccessLimit, IQueryInterceptor arbiter) {
        this.numAccessLimit = numAccessLimit;
        this.arbiter = arbiter;
    }

    @Override
    public ClosableQuery requestConnection(SQLQuery query) throws SQLException, InterruptedException {
        String token = query.getToken();
        Semaphore permits = tokenPermitsMap.computeIfAbsent(token,
                t -> new Semaphore(numAccessLimit));
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

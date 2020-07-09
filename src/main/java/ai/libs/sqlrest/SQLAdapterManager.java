package ai.libs.sqlrest;

import ai.libs.jaicore.db.IDatabaseAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SQLAdapterManager {

    private final Map<String, TokenConnectionHandle> tokenConnectionHandleMap = new ConcurrentHashMap<>();

    private ISLAdapterSupplier provider;

    @Autowired
    public SQLAdapterManager(ISLAdapterSupplier provider) {
        this.provider = provider;
    }

    public List<IDatabaseAdapter> getAdaptersFor(String token) {
        TokenConnectionHandle handle = tokenConnectionHandleMap.computeIfAbsent(token, this::createTokenConnectionHandle);
//        handle.requireNumConnectionsMatchesConfig();
        return handle.getCurrentAdapters();
    }

    private TokenConnectionHandle createTokenConnectionHandle(final String token) {
        TokenConnectionHandle handle = new TokenConnectionHandle(token);
        try {
            handle.loadDatabaseInfo();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        handle.requireNumConnectionsMatchesConfig(provider);
        return handle;
    }

    public int getNumConnections() {
        int numConnections = 0;
        for (Map.Entry<String, TokenConnectionHandle> entry : tokenConnectionHandleMap.entrySet()) {
            numConnections += entry.getValue().getCurrentAdapters().size();
        }
        return numConnections;
    }

    public int getNumConnections(String token) {
        TokenConnectionHandle handle = tokenConnectionHandleMap.getOrDefault(token, null);
        if(handle == null) {
            return 0;
        }
        return handle.getCurrentAdapters().size();
    }
}

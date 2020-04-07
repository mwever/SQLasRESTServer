package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SQLAdapterManager {

    private Map<String, TokenConnectionHandle> tokenConnectionHandleMap = new ConcurrentHashMap<>();

    public List<SQLAdapter> getAdaptersFor(String token) throws SQLException {
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
        handle.requireNumConnectionsMatchesConfig();
        return handle;
    }

}

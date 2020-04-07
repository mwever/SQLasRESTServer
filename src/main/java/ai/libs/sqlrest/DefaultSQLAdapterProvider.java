package ai.libs.sqlrest;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.jaicore.db.sql.SQLAdapter;
import org.aeonbits.owner.ConfigCache;

public class DefaultSQLAdapterProvider implements SQLAdapterProvider {

    public static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    @Override
    public IDatabaseAdapter get(String host, String user, String password, String database) {
        SQLAdapter adapter = new SQLAdapter(host, user, password, database, SERVER_CONFIG.getDBPropUseSsl());
        return adapter;
    }
}

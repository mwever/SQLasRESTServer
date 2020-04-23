package ai.libs.sqlrest.supplier;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.ISLAdapterSupplier;
import ai.libs.sqlrest.IServerConfig;
import org.aeonbits.owner.ConfigCache;

public class DefaultAdapterSupplier implements ISLAdapterSupplier {

    public static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    @Override
    public IDatabaseAdapter get(String user, String password, String database) {
        SQLAdapter adapter = new SQLAdapter(SERVER_CONFIG.getDBHost(), user, password, database,
                SERVER_CONFIG.getDBPropUseSsl());
        return adapter;
    }
}

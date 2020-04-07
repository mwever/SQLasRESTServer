package ai.libs.sqlrest;

import ai.libs.jaicore.db.IDatabaseAdapter;

public interface SQLAdapterProvider {

    IDatabaseAdapter get(final String host, final String user, final String password, final String database);

}

package ai.libs.sqlrest;

import ai.libs.jaicore.db.IDatabaseAdapter;

public interface ISLAdapterSupplier {

    IDatabaseAdapter get(final String user, final String password, final String database);

}

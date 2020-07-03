package ai.libs.sqlrest.supplier;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.sqlrest.ISLAdapterSupplier;

public class CustomAdapterSupplier implements ISLAdapterSupplier {
    @Override
    public IDatabaseAdapter get(String user, String password, String database) {
        return new CustomDatabaseAdapter(user, password, database);
    }
}

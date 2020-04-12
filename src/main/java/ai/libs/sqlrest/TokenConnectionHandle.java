package ai.libs.sqlrest;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.jaicore.db.sql.SQLAdapter;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class TokenConnectionHandle {

    private static final IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);

    private String token;

    private String user, passwd, dbName;

    private AtomicInteger numConnections;

    private List<IDatabaseAdapter> currentAdapters;

    public TokenConnectionHandle(String token) {
        this.token = token;
        currentAdapters = new ArrayList<IDatabaseAdapter>();
        numConnections = new AtomicInteger(0);
    }

    public void loadDatabaseInfo() throws SQLException {
        SQLAdapter adminAdapter = config.createAdminAdapter();
        List<IKVStore> res = adminAdapter.getResultsOfQuery(
                "SELECT * FROM experiments WHERE experiment_token='" + token + "'");
        if (res.isEmpty()) {
            throw new IllegalStateException("No experiment known for the given token!");
        }
        if (res.size() > 1) {
            throw new IllegalStateException("Multiple experiments for the same token. A token must be unique!");
        }
        IKVStore connectionDescription = res.get(0);
        this.user = connectionDescription.getAsString("db_user");
        this.passwd = connectionDescription.getAsString("db_passwd");
        this.dbName = connectionDescription.getAsString("db_name");
        adminAdapter.close();
    }

    public void requireNumConnectionsMatchesConfig(ISLAdapterSupplier provider) {
        int newNumConnections = config.getNumAdapterInstances();
        if(newNumConnections < 1) {
            throw new IllegalArgumentException("Number of connections needs to be positive: " + newNumConnections);
        }
        if(newNumConnections > config.getNumAdapterInstancesLimit()) {
            throw new IllegalArgumentException(String.format("Number of connections cannot " +
                    "exceed the limit of %d: " + newNumConnections, config.getNumAdapterInstancesLimit()));
        }
        int oldVal = this.numConnections.getAndSet(newNumConnections);
        if(oldVal != newNumConnections) {
            readjustNumConnections(provider, newNumConnections);
        }
    }

    private void readjustNumConnections(ISLAdapterSupplier supplier, int newNumConnections) {
        synchronized (this) {
            currentAdapters = new ArrayList<IDatabaseAdapter>(currentAdapters);
            // Copy the list because maybe another thread has returned the original list and it is being used in parallel.
            while(newNumConnections < currentAdapters.size()) {
                IDatabaseAdapter toBeRemoved = currentAdapters.remove(0);
                toBeRemoved.close(); // There is a small probability that the adapter is being used.
            }
            while(newNumConnections > currentAdapters.size()) {
                IDatabaseAdapter adapter = supplier.get(config.getDBHost(), user, passwd, dbName);
                currentAdapters.add(adapter);
            }
        }
    }

    public List<IDatabaseAdapter> getCurrentAdapters() {
        synchronized (this) {
            return currentAdapters;
        }
    }
}

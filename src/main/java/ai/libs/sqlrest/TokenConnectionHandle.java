package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TokenConnectionHandle {

    private static final IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);

    private String token;

    private String user, passwd, dbName;

    private AtomicInteger numConnections;

    private List<SQLAdapter> currentAdapters;

    public TokenConnectionHandle(String token) {
        this.token = token;
        currentAdapters = new ArrayList<>();
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

    public void requireNumConnectionsMatchesConfig() {
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
            readjustNumConnections(newNumConnections);
        }
    }

    private void readjustNumConnections(int newNumConnections) {
        synchronized (this) {
            currentAdapters = new ArrayList<>(currentAdapters);
            while(newNumConnections < currentAdapters.size()) {
                SQLAdapter toBeRemoved = currentAdapters.remove(0);
                toBeRemoved.close();
            }
            while(newNumConnections > currentAdapters.size()) {
                SQLAdapter newAdapter = new SQLAdapter(config.getDBHost(),
                        user, passwd, dbName, config.getDBPropUseSsl());
                currentAdapters.add(newAdapter);
            }
        }
    }

    public List<SQLAdapter> getCurrentAdapters() {
        synchronized (this) {
            return currentAdapters;
        }
    }
}

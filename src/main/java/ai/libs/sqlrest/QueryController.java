package ai.libs.sqlrest;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.model.SQLQuery;

@RestController
public class QueryController {

    private final static Logger logger = LoggerFactory.getLogger(QueryController.class);

	private static final IServerConfig CONFIG = ConfigCache.getOrCreate(IServerConfig.class);
	private static SQLAdapter adminAdapter = null;
	private static Map<String, List<SQLAdapter>> adapterMap = new HashMap<>();
	private static Lock lock = new ReentrantLock();

	private static int numAdapterInstances = 10;

	private void ensureAdminAdapterAvailable() {
		if (adminAdapter == null) {
			adminAdapter = CONFIG.createAdminAdapter();
		}
	}

	private void checkNumInstancesConfig() throws SQLException {
		if (numAdapterInstances != CONFIG.getNumAdapterInstances()) {
			int newNumInstances = CONFIG.getNumAdapterInstances();
			for (Entry<String, List<SQLAdapter>> adaptersEntry : adapterMap.entrySet()) {
				if (adaptersEntry.getValue().size() < newNumInstances) {
					adaptersEntry.getValue().addAll(this.createAdaptersForToken(adaptersEntry.getKey(), newNumInstances - adaptersEntry.getValue().size()));
				} else {
					while (adaptersEntry.getValue().size() > newNumInstances) {
						adaptersEntry.getValue().remove(0);
					}
				}
			}
			numAdapterInstances = newNumInstances;
		}
	}

	private List<SQLAdapter> createAdaptersForToken(final String token, final int numAdapters) throws SQLException {
		List<IKVStore> res = adminAdapter.getResultsOfQuery("SELECT * FROM experiments WHERE experiment_token='" + token + "'");
		if (res.isEmpty()) {
			throw new IllegalStateException("No experiment known for the given token!");
		}
		if (res.size() > 1) {
			throw new IllegalStateException("Multiple experiments for the same token. A token must be unique!");
		}
		IKVStore connectionDescription = res.get(0);
		return IntStream.range(0, numAdapterInstances)
				.mapToObj(x -> new SQLAdapter(CONFIG.getDBHost(), connectionDescription.getAsString("db_user"), connectionDescription.getAsString("db_passwd"), connectionDescription.getAsString("db_name"), CONFIG.getDBPropUseSsl())).collect(Collectors.toList());
	}

	private SQLAdapter getConnector(final String token) throws SQLException {
		lock.lock();
		try {
			this.checkNumInstancesConfig();
			if (!adapterMap.containsKey(token)) {
				this.ensureAdminAdapterAvailable();
				adapterMap.put(token, this.createAdaptersForToken(token, numAdapterInstances));
			}
			return adapterMap.get(token).get(new Random().nextInt(adapterMap.get(token).size()));
		} finally {
			lock.unlock();
		}
	}

	@PostMapping("/query")
	public List<IKVStore> query(@RequestBody final SQLQuery query) throws SQLException, IOException {
		try {
			this.isAllowedQuery(query.getQuery());
		} catch (Exception e) {
			throw new IllegalArgumentException("Query is not allowed", e);
		}
		return this.getConnector(query.getToken()).query(query.getQuery());
	}

	@PostMapping("/update")
	public int update(@RequestBody final SQLQuery query) throws SQLException {
		try {
			this.isAllowedQuery(query.getQuery());
		} catch (Exception e) {
			throw new IllegalArgumentException("Query is not allowed", e);
		}
		int returnValue = this.getConnector(query.getToken()).update(query.getQuery());
		return returnValue;
	}

	@PostMapping("/insert")
	public int[] insert(@RequestBody final SQLQuery query) throws SQLException {
		try {
			this.isAllowedQuery(query.getQuery());
		} catch (Exception e) {
			throw new IllegalArgumentException("Query is not allowed", e);
		}
		return this.getConnector(query.getToken()).insert(query.getQuery(), new LinkedList<>());
	}

	@PostMapping("/numadapter")
    public void setNumAdapterInstances(@RequestBody final Integer numberOfAdapter) {
	    if(CONFIG.isInProduction()) {
	        throw new IllegalStateException("Operation not permitted. The service is in production.");
        }
        if(numberOfAdapter == null) {
            throw new IllegalArgumentException("Number of adapter instances is undefined");
        }
        if(numberOfAdapter < 1) {
            throw new IllegalArgumentException("Number of adapter instances cannot be smaller than 1. Given: " + numberOfAdapter);
        }
        if(numberOfAdapter > CONFIG.getNumAdapterInstancesLimit()) {
            throw new IllegalArgumentException(
                    String.format("Number of adapter instances cannot exceed the defined limit of %d." +
                    " Given: %d", CONFIG.getNumAdapterInstancesLimit(), numberOfAdapter));
        }
        lock.lock();
        try {
            CONFIG.setProperty(IServerConfig.K_NUM_ADAPTER_INSTANCES, String.valueOf(numberOfAdapter));
            logger.info("Set number of adapters = {}", numberOfAdapter);
        } finally {
            lock.unlock();
        }
    }

    @GetMapping("/numadapter")
    public Integer getNumAdapterInstances() {
	    return CONFIG.getNumAdapterInstances();
    }

	private boolean isAllowedQuery(final String query) {
		if (query.contains(";")) {
			throw new IllegalArgumentException("Query contains semicolons (;) which is not allowed.");
		}
		return true;
	}

}

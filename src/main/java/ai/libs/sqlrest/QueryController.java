package ai.libs.sqlrest;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import ai.libs.jaicore.db.IDatabaseAdapter;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ai.libs.sqlrest.model.SQLQuery;

@RestController
public class QueryController {

    private final static Logger logger = LoggerFactory.getLogger(QueryController.class);

    private final static IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);

	private IAdapterArbiter iAdapterArbiter;

	public QueryController(@Qualifier("getAccessImpl") IAdapterArbiter access) {
	    this.iAdapterArbiter = access;
    }

	private IDatabaseAdapter getConnector(final String token) throws SQLException, InterruptedException {
	    return iAdapterArbiter.acquire(token);
	}

	private void giveBackConnector(IDatabaseAdapter adapter, final String token) throws SQLException {
	    iAdapterArbiter.release(adapter, token);
    }

	@PostMapping("/query")
	public List<IKVStore> query(@RequestBody final SQLQuery query) throws SQLException, IOException, InterruptedException {
		try {
			this.assertLegalQuery(query.getQuery());
		} catch (Exception e) {
			throw new IllegalArgumentException("Query is not allowed", e);
		}
        IDatabaseAdapter connector = null;
        String token = query.getToken();
        try {
            connector = this.getConnector(token);
            return connector.query(query.getQuery());
        } finally {
            if (connector != null)
		        giveBackConnector(connector, token);
        }
	}

	@PostMapping("/update")
	public int update(@RequestBody final SQLQuery query) throws SQLException, InterruptedException, IOException {
		try {
			this.assertLegalQuery(query.getQuery());
		} catch (Exception e) {
			throw new IllegalArgumentException("Query is not allowed", e);
		}
        IDatabaseAdapter connector = null;
        String token = query.getToken();
        try {
            connector = this.getConnector(token);
            return connector.update(query.getQuery());
        } finally {
            if (connector != null)
                giveBackConnector(connector, token);
        }
	}

	@PostMapping("/insert")
	public int[] insert(@RequestBody final SQLQuery query) throws SQLException, InterruptedException {
		try {
			this.assertLegalQuery(query.getQuery());
		} catch (Exception e) {
			throw new IllegalArgumentException("Query is not allowed", e);
		}
        IDatabaseAdapter connector = null;
        String token = query.getToken();
        try {
            connector = this.getConnector(token);
            return connector.insert(query.getQuery(), Collections.emptyList());
        } finally {
            if (connector != null)
                giveBackConnector(connector, token);
        }
	}

	private void assertLegalQuery(final String query) {
		if (query.contains(";")) {
			throw new IllegalArgumentException("Query contains semicolons (;) which is not allowed.");
		}
    }

}

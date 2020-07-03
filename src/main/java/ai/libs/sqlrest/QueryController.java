package ai.libs.sqlrest;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import ai.libs.jaicore.db.IDatabaseAdapter;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ai.libs.sqlrest.model.SQLQuery;

@RestController
public class QueryController {

    private final static Logger logger = LoggerFactory.getLogger(QueryController.class);

    private final static IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);

	private IQueryInterceptor iQueryInterceptor;


	private QueryRuntimeModel runtimeModel;

	public QueryController(@Qualifier("interceptorConf") IQueryInterceptor access, QueryRuntimeModel runtimeModel) {
	    this.iQueryInterceptor = access;
	    this.runtimeModel = runtimeModel;
    }

	private ClosableQuery getConnector(final SQLQuery query) throws SQLException, InterruptedException {
	    return iQueryInterceptor.requestConnection(query);
    }

	@PostMapping("/query")
	public List<IKVStore> query(@RequestBody final SQLQuery query) throws SQLException, IOException, InterruptedException {
		try {
			this.assertLegalQuery(query.getQuery());
		} catch (Exception e) {
			throw new IllegalArgumentException("Query is not allowed", e);
		}
        try (ClosableQuery connection = this.getConnector(query)) {
            IDatabaseAdapter connector = connection.getAdapter();
            return connection.getAdapter().query(query.getQuery());
        }
	}

	@PostMapping("/update")
	public int update(@RequestBody final SQLQuery query) throws SQLException, InterruptedException, IOException {
		try {
			this.assertLegalQuery(query.getQuery());
		} catch (Exception e) {
			throw new IllegalArgumentException("Query is not allowed", e);
		}
        try (ClosableQuery connection = this.getConnector(query)) {
            IDatabaseAdapter connector = connection.getAdapter();
            return connector.update(query.getQuery());
        }
	}

	@PostMapping("/insert")
	public int[] insert(@RequestBody final SQLQuery query) throws SQLException, InterruptedException {
		try {
			this.assertLegalQuery(query.getQuery());
		} catch (Exception e) {
			throw new IllegalArgumentException("Query is not allowed", e);
		}
        try (ClosableQuery connection = this.getConnector(query)) {
            IDatabaseAdapter connector = connection.getAdapter();
            return connector.insert(query.getQuery(), Collections.emptyList());
        }
	}

	@GetMapping("/runtime")
    public Map<String, Double> runtime() {
	    return runtimeModel.getQueryTimes();
    }

	private void assertLegalQuery(final String query) {
		if (query.contains(";")) {
			throw new IllegalArgumentException("Query contains semicolons (;) which is not allowed.");
		}
    }

}

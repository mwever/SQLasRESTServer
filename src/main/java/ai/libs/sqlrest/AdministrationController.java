package ai.libs.sqlrest;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.codec.digest.DigestUtils;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.model.Experiment;

@RestController
public class AdministrationController {

	private static final IServerConfig CONFIG = ConfigCache.getOrCreate(IServerConfig.class);
	private static SQLAdapter sql = null;

	@GetMapping("/v1/admin/experiment")
	public Experiment generateNewExperiment(final @RequestParam(name = "name") String name) throws SQLException {
		this.loadDatabaseConnection();
		String token = DigestUtils.sha256Hex(new Random().nextDouble() + "");

		String nameWithoutBlanks = name.replaceAll("\\s", "_");
		String dbNameAndUser = "sqlrest_" + nameWithoutBlanks;
		String password = DigestUtils.sha256Hex(new Random().nextDouble() + "").substring(0, 24);

		Map<String, String> data = new HashMap<>();
		data.put("experiment_name", name);
		data.put("experiment_token", token);
		data.put("db_name", dbNameAndUser);
		data.put("db_user", dbNameAndUser);
		data.put("db_passwd", password);

		// CREATE USER 'wever'@'%' IDENTIFIED WITH caching_sha2_password BY '***';
		// GRANT USAGE ON *.* TO 'wever'@'%';ALTER USER 'wever'@'%' REQUIRE NONE WITH MAX_QUERIES_PER_HOUR 0 MAX_CONNECTIONS_PER_HOUR 0 MAX_UPDATES_PER_HOUR 0 MAX_USER_CONNECTIONS 0;
		// CREATE DATABASE IF NOT EXISTS `wever`;GRANT ALL PRIVILEGES ON `wever`.* TO 'wever'@'%';

		String createUserQuery = "CREATE USER '" + dbNameAndUser + "'@'%' IDENTIFIED WITH caching_sha2_password BY '" + password + "'";
		String grantUsage = "GRANT USAGE ON *.* TO '" + dbNameAndUser + "'@'%'";
		String alterUserForAccess = "ALTER USER '" + dbNameAndUser + "'@'%' REQUIRE NONE WITH MAX_QUERIES_PER_HOUR 0 MAX_CONNECTIONS_PER_HOUR 0 MAX_UPDATES_PER_HOUR 0 MAX_USER_CONNECTIONS 0";
		String createDB = "CREATE DATABASE IF NOT EXISTS `" + dbNameAndUser + "`";
		String grantPrivileges = "GRANT ALL PRIVILEGES ON `" + dbNameAndUser + "`.* TO '" + dbNameAndUser + "'@'%'";
		List<String> queries = Arrays.asList(createUserQuery, grantUsage, alterUserForAccess, createDB, grantPrivileges);

		for (String query : queries) {
			System.out.println("Execute query " + query);
			sql.update(query);
		}

		sql.insert("experiments", data);

		return new Experiment(name, token);
	}

	@GetMapping("/v1/admin/adapterinstances")
	public void setNumAdapterInstances(@RequestParam(name = "numInstances") final int numInstances) {
		CONFIG.setProperty(IServerConfig.K_NUM_ADAPTER_INSTANCES, numInstances + "");
	}

	@GetMapping("/v1/admin/experiment/list")
	public List<IKVStore> getExperiments() throws SQLException {
		this.loadDatabaseConnection();
		return sql.getResultsOfQuery("SELECT * FROM experiments");
	}

	private void loadDatabaseConnection() {
		if (sql == null) {
			sql = new SQLAdapter(CONFIG.getDBHost(), CONFIG.getAdminDBUser(), CONFIG.getAdminDBPassword(), CONFIG.getAdminDBName());
		}
	}

}

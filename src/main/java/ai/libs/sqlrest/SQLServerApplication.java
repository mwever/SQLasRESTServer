package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.sql.SQLException;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class SQLServerApplication {

	private final static Logger logger = LoggerFactory.getLogger(SQLServerApplication.class);

	public static void main(final String[] args) {
		SpringApplication.run(SQLServerApplication.class, args);
		try {
			assertConfigurationIsValid();
		} catch(Exception ex) {
			logger.error("Error checking the sql server configuration: ", ex);
			System.exit(1);
		}
	}

	public static void assertConfigurationIsValid() {
		IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);
		requireNonNull(config, "The sql server configuration is null.");

		requireNonNull(config.getAdminDBName(), "Configuration doesn't define admin data base name.");
		requireNonNull(config.getAdminDBUser(), "Configuration doesn't define admin DB User.");

		requireNonNull(config.getAdminDBPassword(), "Configuration doesn't define admin DB User password.");

		logger.info("Configuration defines {}  many adapter instances. A higher value allowes more clients to be handled, while it also may increase contention and therefore lag.", config.getNumAdapterInstances());
		if(!config.getDBPropUseSsl()) {
			logger.warn("Configuration has disabled the ssl connection to the database.");
		}

		SQLAdapter adminAdapter = config.createAdminAdapter();
		try {
			adminAdapter.checkConnection();
		} catch (SQLException throwables) {
			throw new IllegalStateException("Coudn't connect to the database.", throwables);
		}
	}

}

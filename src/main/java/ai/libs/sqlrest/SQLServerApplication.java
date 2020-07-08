package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.model.ResourceNotFoundException;
import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

import static java.util.Objects.requireNonNull;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class SQLServerApplication {

	private final static Logger logger = LoggerFactory.getLogger(SQLServerApplication.class);

	public static void main(final String[] args) {
		try {
			assertConfigurationIsValid();
		} catch(Exception ex) {
			logger.error("Error checking the sql server configuration: ", ex);
			System.exit(1);
		}
		try {
			createAdminTables();
		} catch(SQLException ex) {
			logger.error("Couldn't create the necessary tables for the SQLasRESTServer.", ex);
			System.exit(1);
		} catch (ResourceNotFoundException ex) {
			logger.warn("Error trying to create tables. Continuing as if the tables exist.",ex);
		}
		SpringApplication.run(SQLServerApplication.class, args);
	}

	private static void createAdminTables() throws SQLException, ResourceNotFoundException {
		IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);
		SQLAdapter adminAdapter = config.createAdminAdapter();
		if(config.isLogSlowQueriesEnabled()) {
			createTables(adminAdapter, "logging");
		}
		createTables(adminAdapter, "experiments");
	}

	private static void createTables(SQLAdapter adminAdapter, String tableName) throws SQLException,
			ResourceNotFoundException {
		String createTableQuery = getCreateTableSQLQuery(tableName);
		try (PreparedStatement ps = adminAdapter.getPreparedStatement(createTableQuery)) {
			ps.execute();
		} catch (SQLSyntaxErrorException ex) {
			// ignore an error thrown when the table already exists:
			if(!ex.getMessage().matches("Table '.*' already exists")){
				throw ex;
			}
		}
	}

	private static String getCreateTableSQLQuery(String tableName) throws ResourceNotFoundException {
		final String resourceFileName = String.format("create_%s_table.sql", tableName);
		ByteSource byteSource = new ByteSource() {
			@Override public InputStream openStream() throws IOException {
				InputStream resource = Thread.currentThread()
						.getContextClassLoader().getResourceAsStream(resourceFileName);
				if(resource == null) {
					throw new IOException("Resource not found.");
				} else {
					return resource;
				}
			}
		};
		try {
			return byteSource.asCharSource(Charsets.UTF_8).read();
		} catch (IOException e) {
			throw new ResourceNotFoundException("Couldn't read sql query from resource: " + resourceFileName, e);
		}
	}

	private static void assertConfigurationIsValid() {
		IServerConfig config = ConfigCache.getOrCreate(IServerConfig.class);
		requireNonNull(config, "The sql server configuration is null.");

		requireNonNull(config.getAdminDBName(), "Configuration doesn't define admin data base name.");
		requireNonNull(config.getAdminDBUser(), "Configuration doesn't define admin DB User.");

		requireNonNull(config.getAdminDBPassword(), "Configuration doesn't define admin DB User password.");

		logger.info("Configuration defines {}  many adapter instances. A higher value allows more clients to be handled, while it also may increase contention and therefore lag.", config.getNumAdapterInstances());
		if(!config.getDBPropUseSsl()) {
			logger.warn("Configuration has disabled the ssl connection to the database.");
		}

		SQLAdapter adminAdapter = config.createAdminAdapter();
		try {
			adminAdapter.checkConnection();
		} catch (SQLException ex) {
			throw new IllegalStateException("Couldn't connect to the database.", ex);
		}
	}

}

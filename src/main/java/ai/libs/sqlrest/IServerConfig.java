package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Mutable;

@Sources({ "file:conf/server.properties" })
public interface IServerConfig extends Mutable {

	public static final String K_DB_HOST = "db.host";


    public static final String K_DB_PROP_VERIFY_SERVER_CERT = "db.prop.verifyServerCertificate";
    public static final String K_DB_PROP_REQUIRE_SSL = "db.prop.requireSSL";
    public static final String K_DB_PROP_USE_SSL = "db.prop.useSSL";

	public static final String K_ADMIN_DB_NAME = "db.backend.name";
	public static final String K_ADMIN_DB_USER = "db.backend.user";
	public static final String K_ADMIN_DB_PASSWD = "db.backend.passwd";

    public static final String K_NUM_ADAPTER_INSTANCES = "server.adapter.instances";
    public static final String K_NUM_ADAPTER_INSTANCES_LIMIT = "server.adapter.instancesLimit";
    public static final String K_NUM_ADAPTER_ACCESS_LIMIT = "server.adapter.accessLimit";

    public static final String K_ADAPTER_ACCESS_RANDOM = "server.adapter.randomAccess";
    public static final String K_ADAPTER_LIMIT_ACCESS = "server.adapter.accessLimited";

    public static final String K_SERVER_LOGGING_LOG_SLOW_QUERIES = "server.logging.logSlowQueries";
    public static final String K_SERVER_LOGGING_SLOW_QUERY_THRESHOLD = "server.logging.slowQueryThreshold";
    public static final String K_SERVER_LOGGING_DYNAMIC_SLOW_QUERY_THRESHOLD = "server.logging.dynamicSlowQueryThreshold";
    public static final String K_SERVER_LOGGING_DYNAMIC_SLOW_QUERY_THRESHOLD_MIN_LIMIT = "server.logging.dynamicSlowQueryThresholdMinLimit";
    public static final String K_SERVER_LOGGING_SLOWEST_QUERIES_QUANTILE = "server.logging.slowestQueriesQuantile";

    public static final String K_DB_PROP_USE_COMPRESSION = "db.prop.useCompression";

    public static final String K_DB_PROP_FETCH_SIZE = "db.prop.defaultFetchSize";

    public static final String K_DB_PROP_USE_SERVER_PREP_STMTS = "db.prop.useServerPrepStmts";

    public static final String K_DB_PROP_CACHE_PREP_STMTS = "db.prop.cachePrepStmts";

    public static final String K_DB_PROP_USE_READ_AHEAD_INPUT = "db.prop.useReadAheadInput";

    public static final String K_DB_PROP_CACHE_SERVER_CONF = "db.prop.cacheServerConf";
    public static final String K_DB_PROP_CACHE_PREP_STMT_SIZE = "db.prop.cachePrepStmtSize";
    public static final String K_DB_PROP_CACHE_PREP_STMT_SQL_LIMIT = "db.prop.cachePrepStmtSqlLimit";
    public static final String K_DB_PROP_USE_UNBUFFERED_INPUT = "db.prop.useUnbufferedInput";


    @Key(K_DB_HOST)
    public String getDBHost();

//    @Key(K_DB_PROP_VERIFY_SERVER_CERT)
//    public String getDBPropVerifyServerCert();
//
//    @Key(K_DB_PROP_REQUIRE_SSL)
//    public String getDBPropRequireSsl();

    @Key(K_DB_PROP_USE_SSL)
    @DefaultValue("true")
    public Boolean getDBPropUseSsl();

    @Key(K_ADMIN_DB_USER)
	public String getAdminDBUser();

	@Key(K_ADMIN_DB_PASSWD)
	public String getAdminDBPassword();

	@Key(K_ADMIN_DB_NAME)
	public String getAdminDBName();

    @Key(K_NUM_ADAPTER_INSTANCES)
    @DefaultValue("1")
    public int getNumAdapterInstances();

    /**
     * Returns number of adapter instances that are created.
     */
    @Key(K_NUM_ADAPTER_INSTANCES_LIMIT)
    @DefaultValue("16")
    int getNumAdapterInstancesLimit();

    /*
     * Returns true if requests pick adapters randomly.
     * Else requests access adapters by round-robin.
     */
    @Key(K_ADAPTER_ACCESS_RANDOM)
    @DefaultValue("true")
    public boolean isAccessRandom();

    /**
     * Returns true if access to adapters of a specific token is limited to a `getNumAdapterAccessLimit()` amount.
     * Remaining requests are blocked.
     */
    @Key(K_ADAPTER_LIMIT_ACCESS)
    @DefaultValue("false")
    public boolean isAccessLimited();

    /**
     * Returns number of parallel requests that get accessed to adapters of a specific token if the `isAccessLimited()` is true.
     */
    @Key(K_NUM_ADAPTER_ACCESS_LIMIT)
    @DefaultValue("1")
    public int getNumAdapterAccessLimit();


    @Key(K_SERVER_LOGGING_LOG_SLOW_QUERIES)
    @DefaultValue("true")
    public boolean isLogSlowQueriesEnabled();

    @Key(K_SERVER_LOGGING_SLOW_QUERY_THRESHOLD)
    @DefaultValue("2500")
    public long slowQueryThreshold();

    @Key(K_SERVER_LOGGING_DYNAMIC_SLOW_QUERY_THRESHOLD)
    @DefaultValue("true")
    public boolean isQueryThresholdDynamic();

    @Key(K_SERVER_LOGGING_SLOWEST_QUERIES_QUANTILE)
    @DefaultValue("0.995")
    public double slowestQueriesQuantile();

    @Key(K_SERVER_LOGGING_DYNAMIC_SLOW_QUERY_THRESHOLD_MIN_LIMIT)
    @DefaultValue("500")
    public long slowQueryDynamicMinLimit();

    /**
     * Creates and returns a SQLAdapter based on the admin user and db information defined by this configuration.
     *
     * @return SQLAdapter connnected to the admin database.
     */
	default SQLAdapter createAdminAdapter() {
//	    Properties connectionProps = new Properties();
//        connectionProps.put("verifyServerCertificate", getDBPropUseSsl());
//        connectionProps.put("requireSSL", getDBPropRequireSsl());
//        connectionProps.put("useSSL", getDBPropUseSsl());

        SQLAdapter adminAdapter = new SQLAdapter(getDBHost(), getAdminDBUser(),
                getAdminDBPassword(), getAdminDBName(),
                getDBPropUseSsl());
        return adminAdapter;
    }

    /*
     * Performance properties:
     */

    @Key(K_DB_PROP_USE_COMPRESSION)
    @DefaultValue("false")
    boolean getDBPropUseCompression();

    @Key(K_DB_PROP_FETCH_SIZE)
    @DefaultValue("0")
    int getDBPropFetchSize();

    @Key(K_DB_PROP_USE_SERVER_PREP_STMTS)
    @DefaultValue("false")
    boolean getDBPropUseServerSidePrepStmts();

    @Key(K_DB_PROP_CACHE_PREP_STMTS)
    @DefaultValue("false")
    boolean getDBPropCachePrepStmts();

    @Key(K_DB_PROP_USE_READ_AHEAD_INPUT)
    @DefaultValue("true")
    boolean getDBPropReadAheadInput();

    @Key(K_DB_PROP_CACHE_SERVER_CONF)
    @DefaultValue("false")
    boolean getDBPropCacheServerConf();

    @Key(K_DB_PROP_CACHE_PREP_STMT_SIZE)
    @DefaultValue("25")
    int getDBPropCachePrepStmtsSize();

    @Key(K_DB_PROP_CACHE_PREP_STMT_SQL_LIMIT)
    @DefaultValue("256")
    int getDBPropCachePrepStmtsSqlLimit();

    @Key(K_DB_PROP_USE_UNBUFFERED_INPUT)
    @DefaultValue("true")
    boolean getDPPropUseUnbufferedInput();
}

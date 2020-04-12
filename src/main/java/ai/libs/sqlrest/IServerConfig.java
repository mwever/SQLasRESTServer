package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Mutable;

import java.util.Properties;

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

}

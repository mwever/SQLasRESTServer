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

    @Key(K_NUM_ADAPTER_INSTANCES_LIMIT)
    @DefaultValue("16")
    public int getNumAdapterInstancesLimit();

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

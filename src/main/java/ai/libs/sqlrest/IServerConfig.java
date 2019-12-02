package ai.libs.sqlrest;

import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Mutable;

@Sources({ "file:conf/server.properties" })
public interface IServerConfig extends Mutable {

	public static final String K_DB_HOST = "db.host";

	public static final String K_ADMIN_DB_NAME = "db.backend.name";
	public static final String K_ADMIN_DB_USER = "db.backend.user";
	public static final String K_ADMIN_DB_PASSWD = "db.backend.passwd";

	public static final String K_NUM_ADAPTER_INSTANCES = "server.adapter.instances";

	@Key(K_DB_HOST)
	public String getDBHost();

	@Key(K_ADMIN_DB_USER)
	public String getAdminDBUser();

	@Key(K_ADMIN_DB_PASSWD)
	public String getAdminDBPassword();

	@Key(K_ADMIN_DB_NAME)
	public String getAdminDBName();

	@Key(K_NUM_ADAPTER_INSTANCES)
	public int getNumAdapterInstances();

}

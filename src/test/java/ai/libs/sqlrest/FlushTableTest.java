package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class FlushTableTest {

    @Test
    public void testFlushCommands() throws IOException, SQLException {
        IServerConfig conf = ConfigCache.getOrCreate(IServerConfig.class);
        SQLAdapter sqlAdapter = conf.createAdminAdapter();
        List<IKVStore> query = sqlAdapter.query("FLUSH HOSTS; FLUSH TABLES;");
        assert query.isEmpty();

    }
}

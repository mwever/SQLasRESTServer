package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

public class ISQLAdapterRandomAccess implements ISQLAdapterAccess {

    private final SQLAdapterManager adapterManager;

    public ISQLAdapterRandomAccess(SQLAdapterManager adapterManager) {
        this.adapterManager = adapterManager;
    }

    @Override
    public SQLAdapter acquire(String token) throws SQLException {
        List<SQLAdapter> adapters = adapterManager.getAdaptersFor(token);
        int size = adapters.size();
        int randomIndex = (int) (Math.random() * size);
        return adapters.get(randomIndex);
    }

    @Override
    public void release(SQLAdapter adapter, String token) throws SQLException {

    }

}

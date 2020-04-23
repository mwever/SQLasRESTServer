package ai.libs.sqlrest;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.sqlrest.model.SQLQuery;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClosableQuery implements AutoCloseable{

    private final SQLQuery query;

    private final IDatabaseAdapter adapter;

    private final List<ConnectionCloseHook> closeHooks = new ArrayList<>();

    public ClosableQuery(IDatabaseAdapter adapter, SQLQuery query) {
        this.adapter = adapter;
        this.query = query;
    }

    public IDatabaseAdapter getAdapter() {
        return adapter;
    }

    public SQLQuery getQuery() {
        return query;
    }

    public void addCloseHook(ConnectionCloseHook closeHook) {
        this.closeHooks.add(closeHook);
    }

    @Override
    public void close() throws SQLException {
        for (ConnectionCloseHook closeHook : closeHooks) {
            closeHook.action(this);
        }
    }

    public interface ConnectionCloseHook {
        void action(ClosableQuery access) throws SQLException;
    }

}

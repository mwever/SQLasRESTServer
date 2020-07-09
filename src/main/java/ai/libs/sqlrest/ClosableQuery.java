package ai.libs.sqlrest;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.sqlrest.model.SQLQuery;
import org.apache.commons.math3.analysis.function.Exp;

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
        List<SQLException> exceptions = new ArrayList<>();
        for (ConnectionCloseHook closeHook : closeHooks) {
            try {
                closeHook.action(this);
            } catch (SQLException exception) {
                exceptions.add(exception);
            }
        }
        if(!exceptions.isEmpty()) {
            throw new SQLException("Error while executing close hooks: " + exceptions.size(), exceptions.get(0));
        }
    }

    public interface ConnectionCloseHook {
        void action(ClosableQuery access) throws SQLException;
    }

}

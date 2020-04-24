package ai.libs.sqlrest.interceptors;

import ai.libs.sqlrest.ClosableQuery;
import ai.libs.sqlrest.IQueryInterceptor;
import ai.libs.sqlrest.QueryRuntimeModel;
import ai.libs.sqlrest.model.SQLQuery;

import java.sql.SQLException;

public class QueryTimeRecorder implements IQueryInterceptor {

    private final IQueryInterceptor prevInterceptor;

    private final QueryRuntimeModel runtimeModel;

    public QueryTimeRecorder(IQueryInterceptor prevInterceptor, QueryRuntimeModel runtimeModel) {
        this.runtimeModel = runtimeModel;
        this.prevInterceptor = prevInterceptor;
    }

    @Override
    public ClosableQuery requestConnection(SQLQuery query) throws SQLException, InterruptedException {
        final ClosableQuery closableQuery = prevInterceptor.requestConnection(query);
        final long timeWhenQueried = System.currentTimeMillis();
        closableQuery.addCloseHook(c ->
                runtimeModel.recordQueryTime(System.currentTimeMillis() - timeWhenQueried));
        return closableQuery;
    }

}

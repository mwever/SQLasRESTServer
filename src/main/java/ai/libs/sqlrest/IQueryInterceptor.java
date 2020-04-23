package ai.libs.sqlrest;

import ai.libs.sqlrest.model.SQLQuery;

import java.sql.SQLException;

public interface IQueryInterceptor {

    ClosableQuery requestConnection(SQLQuery query) throws SQLException, InterruptedException;


}

package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public interface ISQLAdapterAccess {

    SQLAdapter acquire(String token) throws SQLException, InterruptedException;

    void release(SQLAdapter adapter, String token) throws SQLException;

}

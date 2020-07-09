package ai.libs.sqlrest;

import ai.libs.sqlrest.model.SQLQuery;

public interface IDBQueryLogger {

    int logTimeOut(SQLQuery query,
                   long timeStarted, long queryThreshold, String execTime,
                   int numRequestsSinceQuery, int numUnfinishedRequestsSinceQuery) throws Exception;

    void updateFinished(int id, long execTime) throws Exception;

}

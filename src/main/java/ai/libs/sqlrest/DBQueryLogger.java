package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.model.SQLQuery;
import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DBQueryLogger implements IDBQueryLogger {

    private final static Logger logger = LoggerFactory.getLogger(DBQueryLogger.class);

    private static final long SESSION_ID = System.currentTimeMillis();

    public static final String LOG_REASON_TIMEOUT = "QUERY_TIMED_OUT";

    private static final IServerConfig CONF = ConfigCache.getOrCreate(IServerConfig.class);

    private final SQLAdapterManager manager;

    private String insertionQuery, updateQuery;

    private SQLAdapter adapter;

    @Autowired
    public DBQueryLogger(SQLAdapterManager manager) {
        this.manager = manager;
    }

    private synchronized SQLAdapter getAdapter() {
        if(adapter == null) {
            adapter = CONF.createAdminAdapter();
        }
        return adapter;
    }

    private synchronized String getInsertionString() {
        if (insertionQuery == null) {
            ClassPathResource insertionSQLResource;
            insertionSQLResource = new ClassPathResource("insert_logging_table.sql");
            try (InputStream in = insertionSQLResource.getInputStream()) {
                Scanner scanner = new Scanner(in);
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNext()) {
                    sb.append(scanner.nextLine());
                }
                insertionQuery = sb.toString();
            } catch (IOException e) {
                throw new RuntimeException("Couldn't read insertion string from resources: ", e);
            }
        }
        return insertionQuery;
    }

    private synchronized String getUpdateQuery() {
        if (updateQuery == null) {
            ClassPathResource insertionSQLResource;
            insertionSQLResource = new ClassPathResource("update_logging_table_exectime.sql");
            try (InputStream in = insertionSQLResource.getInputStream()) {
                Scanner scanner = new Scanner(in);
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNext()) {
                    sb.append(scanner.nextLine());
                }
                updateQuery = sb.toString();
            } catch (IOException e) {
                throw new RuntimeException("Couldn't read update string from resources: ", e);
            }
        }
        return updateQuery;
    }

    private int getNumJVMThreads() {
        return Thread.activeCount();
    }

    private long getUsedJVMMemory() {
        return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
    }

    private long getFreeJVMMemory() {
        return Runtime.getRuntime().freeMemory();
    }


    public int logTimeOut(SQLQuery query,
                          long timeStarted, long queryThreshold, String execTime,
                          int numRequestsSinceQuery, int numUnfinishedRequestsSinceQuery) throws Exception {
        SQLAdapter adapter = getAdapter();
        String insertionString = getInsertionString();
        int numConnections = manager.getNumConnections();
        int numConnectionsToken = manager.getNumConnections(query.getToken());
        int numJVMThreads = getNumJVMThreads();
        long usedJVMMemory = getUsedJVMMemory();
        long freeJVMMemory = getFreeJVMMemory();
        String timeStartedStr = new java.sql.Timestamp(timeStarted).toString();

        String[] values = {
            String.valueOf(SESSION_ID), LOG_REASON_TIMEOUT,
            query.getToken(), query.getQuery(),
            timeStartedStr, execTime, String.valueOf(queryThreshold),
            String.valueOf(numConnections), String.valueOf(numConnectionsToken),
            String.valueOf(numRequestsSinceQuery), String.valueOf(numUnfinishedRequestsSinceQuery),
            String.valueOf(numJVMThreads), String.valueOf(usedJVMMemory), String.valueOf(freeJVMMemory)
        };

        if(logger.isDebugEnabled()) {
            logger.debug("Query timed out:\n" +
                            "token: {}, query: {},\n" +
                            "time started: {}, timeout threshold: {},\n" +
                            "num db connections: {},\n" +
                            "num requests since query: {}, unfinished: {}\n" +
                            "num jvm threads: {}, used jvm memory: {} Mbyte, free jvm memory: {} Mbyte",
                    query.getToken(), query.getQuery(),
                    timeStarted, queryThreshold,
                    numConnections,
                    numRequestsSinceQuery, numUnfinishedRequestsSinceQuery,
                    numJVMThreads, ((usedJVMMemory /10000)/100.), ((freeJVMMemory /10000)/100.));
        }

        int[] ids = adapter.insert(insertionString, values);

        if(ids == null || ids.length != 1) {
            throw new SQLException("No id returned: " +
                    (ids == null? "null" : Arrays.toString(ids)));
        }
        return ids[0];
    }

    public void updateFinished(int id, long execTime) throws Exception {
        SQLAdapter adapter = getAdapter();
        String[] values = {
                String.valueOf(execTime), String.valueOf(id)
        };
        adapter.update(getUpdateQuery(), values);
    }
}

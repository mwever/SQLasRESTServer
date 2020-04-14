package ai.libs.sqlrest.supplier;

import ai.libs.jaicore.basic.sets.Pair;
import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.jaicore.db.sql.ISQLQueryBuilder;
import ai.libs.jaicore.db.sql.MySQLQueryBuilder;
import ai.libs.jaicore.db.sql.ResultSetToKVStoreSerializer;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class CustomServiceAdapter implements IDatabaseAdapter {

    // A static field to speed up creation of instances.
    private static Logger logger = LoggerFactory.getLogger(CustomServiceAdapter.class);

    private static final ResultSetToKVStoreSerializer SERIALIZER = new ResultSetToKVStoreSerializer();

    private static final String KEY_EQUALS_VALUE_TO_BE_SET = " = (?)";

    private static final String STR_SPACE_AND = " AND ";
    private static final String STR_SPACE_WHERE = " WHERE ";

    private final BaseConnectionHandler connect;

    private final ISQLQueryBuilder queryBuilder = new MySQLQueryBuilder();

    public CustomServiceAdapter(String user, String password, String databaseName) {
        connect = new BaseConnectionHandler(new DefaultConnectionSupplier(), user, password, databaseName);
    }

    @Override
    public void checkConnection() throws SQLException {
        if(connect.getConnection() == null) {
            throw new SQLException("No connection present.");
        }
    }

    @Override
    public void close() {
        try {
            connect.closeConnection();
        } catch(Exception ex) {
            logger.warn("An error occurred trying to close the connection.", ex);
        }
    }

    @Override
    public String getLoggerName() {
        return logger.getName();
    }

    @Override
    public void setLoggerName(String name) {
        logger = LoggerFactory.getLogger(name);
    }

    /**
     * Retrieves all rows of a table.
     * @param table The table for which all entries shall be returned.
     * @return A list of {@link IKVStore}s containing the data of the table.
     * @throws SQLException Thrown, if there was an issue with the connection to the database.
     */
    @Override
    public List<IKVStore> getRowsOfTable(final String table) throws SQLException {
        logger.info("Fetching complete table {}", table);
        return this.getRowsOfTable(table, new HashMap<>());
    }

    /**
     * Retrieves all rows of a table which satisfy certain conditions (WHERE clause).
     * @param table The table for which all entries shall be returned.
     * @param conditions The conditions a result entry must satisfy.
     * @return A list of {@link IKVStore}s containing the data of the table.
     * @throws SQLException Thrown, if there was an issue with the connection to the database.
     */
    @Override
    public List<IKVStore> getRowsOfTable(final String table, final Map<String, String> conditions) throws SQLException {
        return this.getResultsOfQuery(this.queryBuilder.buildSelectSQLCommand(table, conditions));
    }


    public Iterator<IKVStore> getRowIteratorOfTable(final String table) throws SQLException {
        return this.getRowIteratorOfTable(table, new HashMap<>());
    }

    public Iterator<IKVStore> getRowIteratorOfTable(final String table, final Map<String, String> conditions) throws SQLException {
        StringBuilder conditionSB = new StringBuilder();
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : conditions.entrySet()) {
            if (conditionSB.length() > 0) {
                conditionSB.append(STR_SPACE_AND);
            } else {
                conditionSB.append(STR_SPACE_WHERE);
            }
            conditionSB.append(entry.getKey() + KEY_EQUALS_VALUE_TO_BE_SET);
            values.add(entry.getValue());
        }
        return this.getResultIteratorOfQuery("SELECT * FROM `" + table + "`" + conditionSB.toString(), values);
    }

    /**
     * Retrieves the select result for the given query.
     * @param query The SQL query which is to be executed.
     * @return A list of {@link IKVStore}s containing the result data of the query.
     * @throws SQLException Thrown, if there was an issue with the connection to the database.
     */
    @Override
    public List<IKVStore> getResultsOfQuery(final String query) throws SQLException {
        return this.getResultsOfQuery(query, new ArrayList<>());
    }

    /**
     * Retrieves the select result for the given query that can have placeholders.
     * @param query The SQL query which is to be executed (with placeholders).
     * @param values An array of placeholder values that need to be filled in.
     * @return A list of {@link IKVStore}s containing the result data of the query.
     * @throws SQLException Thrown, if there was an issue with the connection to the database.
     */
    @Override
    public List<IKVStore> getResultsOfQuery(final String query, final String[] values) throws SQLException {
        return this.getResultsOfQuery(query, Arrays.asList(values));
    }

    /**
     * Retrieves the select result for the given query that can have placeholders.
     * @param query The SQL query which is to be executed (with placeholders).
     * @param values A list of placeholder values that need to be filled in.
     * @return A list of {@link IKVStore}s containing the result data of the query.
     * @throws SQLException Thrown, if there was an issue with the query format or the connection to the database.
     */
    @Override
    public List<IKVStore> getResultsOfQuery(final String query, final List<String> values) throws SQLException {
        this.checkConnection();
        logger.info("Conducting query {} with values {}", query, values);
        try (PreparedStatement statement = this.connect.getConnection().prepareStatement(query)) {
            for (int i = 1; i <= values.size(); i++) {
                statement.setString(i, values.get(i - 1));
            }
            return SERIALIZER.serialize(statement.executeQuery());
        }
    }

    public Iterator<IKVStore> getResultIteratorOfQuery(final String query, final List<String> values) throws SQLException {
        this.checkConnection();
        boolean autoCommit = this.connect.getConnection().getAutoCommit();
        this.connect.getConnection().setAutoCommit(false); // deactivate autocommit for this request
        logger.info("Conducting query {} with values {}", query, values);
        PreparedStatement statement = this.connect.getConnection().prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(100); // this avoids that the whole result table is read
        for (int i = 1; i <= values.size(); i++) {
            statement.setString(i, values.get(i - 1));
        }
        Iterator<IKVStore> iterator = SERIALIZER.getSerializationIterator(statement.executeQuery());
        this.connect.getConnection().setAutoCommit(autoCommit);
        return iterator;
    }

    /**
     * Executes an insert query and returns the row ids of the created entries.
     * @param sql The insert statement which shall be executed that may have placeholders.
     * @param values The values for the placeholders.
     * @return An array of the row ids of the inserted entries.
     * @throws SQLException Thrown, if there was an issue with the query format or the connection to the database.
     */
    @Override
    public int[] insert(final String sql, final String[] values) throws SQLException {
        return this.insert(sql, Arrays.asList(values));
    }

    /**
     * Executes an insert query and returns the row ids of the created entries.
     * @param sql The insert statement which shall be executed that may have placeholders.
     * @param values A list of values for the placeholders.
     * @return An array of the row ids of the inserted entries.
     * @throws SQLException Thrown, if there was an issue with the query format or the connection to the database.
     */
    @Override
    public int[] insert(final String sql, final List<? extends Object> values) throws SQLException {
        this.checkConnection();
        try (PreparedStatement stmt = this.connect.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= values.size(); i++) {
                this.setValue(stmt, i, values.get(i - 1));
            }
            stmt.executeUpdate();
            List<Integer> generatedKeys = new LinkedList<>();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                while (rs.next()) {
                    generatedKeys.add(rs.getInt(1));
                }
            }
            return generatedKeys.stream().mapToInt(x -> x).toArray();
        }
    }

    /**
     * Creates and executes an insert query for the given table and the values as specified in the map.
     * @param table The table where to insert the data.
     * @param map The map of key:value pairs to be inserted into the table.
     * @return An array of the row ids of the inserted entries.
     * @throws SQLException Thrown, if there was an issue with the query format or the connection to the database.
     */
    @Override
    public int[] insert(final String table, final Map<String, ? extends Object> map) throws SQLException {
        Pair<String, List<Object>> insertStatement = this.queryBuilder.buildInsertStatement(table, map);
        return this.insert(insertStatement.getX(), insertStatement.getY());
    }

    /**
     * Creates a multi-insert statement and executes it. The returned array contains the row id's of the inserted rows. (By default it creates chunks of size 10.000 rows per query to be inserted.)
     * @param table The table to which the rows are to be added.
     * @param keys The list of column keys for which values are set.
     * @param datarows The list of value lists to be filled into the table.
     * @return An array of row id's of the inserted rows.
     * @throws SQLException Thrown, if the sql statement was malformed, could not be executed, or the connection to the database failed.
     */
    @Override
    public int[] insertMultiple(final String table, final List<String> keys, final List<List<? extends Object>> datarows) throws SQLException {
        return this.insertMultiple(table, keys, datarows, 10000);
    }

    /**
     * Creates a multi-insert statement and executes it. The returned array contains the row id's of the inserted rows.
     * @param table The table to which the rows are to be added.
     * @param keys The list of column keys for which values are set.
     * @param datarows The list of value lists to be filled into the table.
     * @param chunkSize The number of rows which are added within one single database transaction. (10,000 seems to be a good value for this)
     * @return An array of row id's of the inserted rows.
     * @throws SQLException Thrown, if the sql statement was malformed, could not be executed, or the connection to the database failed.
     */
    @Override
    public int[] insertMultiple(final String table, final List<String> keys, final List<List<? extends Object>> datarows, final int chunkSize) throws SQLException {
        int n = datarows.size();
        List<Integer> ids = new ArrayList<>(n);
        try (Statement stmt = this.connect.getConnection().createStatement()) {
            for (int i = 0; i < Math.ceil(n * 1.0 / chunkSize); i++) {
                int startIndex = i * chunkSize;
                int endIndex = Math.min((i + 1) * chunkSize, n);
                String sql = this.queryBuilder.buildMultiInsertSQLCommand(table, keys, datarows.subList(startIndex, endIndex));
                logger.debug("Created SQL for {} entries", endIndex - startIndex);
                logger.trace("Adding sql statement {} to batch", sql);
                stmt.addBatch(sql);
            }
            logger.debug("Start batch execution.");
            stmt.executeBatch();
            logger.debug("Finished batch execution.");
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
            return ids.stream().mapToInt(x -> x).toArray();
        }
    }

    /**
     * Execute the given sql statement as an update.
     * @param sql The sql statement to be executed.
     * @return The number of rows affected by the update statement.
     * @throws SQLException Thrown if the statement is malformed or an issue while executing the sql statement occurs.
     */
    @Override
    public int update(final String sql) throws SQLException {
        return this.update(sql, new ArrayList<String>());
    }

    /**
     * Execute the given sql statement with placeholders as an update filling the placeholders with the given values beforehand.
     * @param sql The sql statement with placeholders to be executed.
     * @param sql Array of values for the respective placeholders.
     * @return The number of rows affected by the update statement.
     * @throws SQLException Thrown if the statement is malformed or an issue while executing the sql statement occurs.
     */
    @Override
    public int update(final String sql, final String[] values) throws SQLException {
        return this.update(sql, Arrays.asList(values));
    }

    /**
     * Execute the given sql statement with placeholders as an update filling the placeholders with the given values beforehand.
     * @param sql The sql statement with placeholders to be executed.
     * @param values List of values for the respective placeholders.
     * @return The number of rows affected by the update statement.
     * @throws SQLException Thrown if the statement is malformed or an issue while executing the sql statement occurs.
     */
    @Override
    public int update(final String sql, final List<? extends Object> values) throws SQLException {
        this.checkConnection();
        try (PreparedStatement stmt = this.connect.getConnection().prepareStatement(sql)) {
            for (int i = 1; i <= values.size(); i++) {
                stmt.setString(i, values.get(i - 1).toString());
            }
            return stmt.executeUpdate();
        }
    }

    /**
     * Create and execute an update statement for some table updating the values as described in <code>updateValues</code> and only affect those entries satisfying the <code>conditions</code>.
     * @param table The table which is to be updated.
     * @param updateValues The description how entries are to be updated.
     * @param conditions The description of the where-clause, conditioning the entries which are to be updated.
     * @return The number of rows affected by the update statement.
     * @throws SQLException Thrown if the statement is malformed or an issue while executing the sql statement occurs.
     */
    @Override
    public int update(final String table, final Map<String, ? extends Object> updateValues, final Map<String, ? extends Object> conditions) throws SQLException {
        this.checkConnection();

        // build the update mapping.
        StringBuilder updateSB = new StringBuilder();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, ? extends Object> entry : updateValues.entrySet()) {
            if (updateSB.length() > 0) {
                updateSB.append(", ");
            }
            updateSB.append(entry.getKey() + KEY_EQUALS_VALUE_TO_BE_SET);
            values.add(entry.getValue());
        }

        // build the condition restricting the elements which are affected by the update.
        StringBuilder conditionSB = new StringBuilder();
        for (Map.Entry<String, ? extends Object> entry : conditions.entrySet()) {
            if (conditionSB.length() > 0) {
                conditionSB.append(STR_SPACE_AND);
            }
            if (entry.getValue() != null) {
                conditionSB.append(entry.getKey() + KEY_EQUALS_VALUE_TO_BE_SET);
                values.add(entry.getValue());
            } else {
                conditionSB.append(entry.getKey());
                conditionSB.append(" IS NULL");
            }
        }

        // Build query for the update command.
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("UPDATE ");
        sqlBuilder.append(table);
        sqlBuilder.append(" SET ");
        sqlBuilder.append(updateSB.toString());
        sqlBuilder.append(STR_SPACE_WHERE);
        sqlBuilder.append(conditionSB.toString());

        try (PreparedStatement stmt = this.connect.getConnection().prepareStatement(sqlBuilder.toString())) {
            for (int i = 1; i <= values.size(); i++) {
                this.setValue(stmt, i, values.get(i - 1));
            }
            return stmt.executeUpdate();
        }
    }

    /**
     * Executes the given statements atomically. Only works if no other statements are sent through this adapter in parallel! Only use for single-threaded applications, otherwise side effects may happen as this changes the auto commit
     * settings of the connection temporarily.
     *
     * @param queries
     *            The queries to execute atomically
     * @throws SQLException
     *             If the status of the connection cannot be changed. If something goes wrong while executing the given statements, they are rolled back before they are committed.
     */
    @Override
    public void executeQueriesAtomically(final List<PreparedStatement> queries) throws SQLException {
        this.checkConnection();
        this.connect.getConnection().setAutoCommit(false);

        try {
            for (PreparedStatement query : queries) {
                query.execute();
            }
            this.connect.getConnection().commit();
        } catch (SQLException e) {
            logger.error("Transaction is being rolled back.", e);
            try {
                this.connect.getConnection().rollback();
            } catch (SQLException e1) {
                logger.error("Could not rollback the connection", e1);
            }
        } finally {
            for (PreparedStatement query : queries) {
                if (query != null) {
                    query.close();
                }
            }

            this.connect.getConnection().setAutoCommit(true);
        }
    }

    @Override
    public List<IKVStore> query(final String sqlStatement) throws SQLException, IOException {
        this.checkConnection();
        try (PreparedStatement ps = this.connect.getConnection().prepareStatement(sqlStatement)) {
            boolean success = ps.execute();
            if (success) {
                return SERIALIZER.serialize(ps.getResultSet());
            } else {
                return new LinkedList<>();
            }
        }
    }

    private void setValue(final PreparedStatement stmt, final int index, final Object val) throws SQLException {
        if (val instanceof Integer) {
            stmt.setInt(index, (Integer) val);
        } else if (val instanceof Long) {
            stmt.setLong(index, (Long) val);
        } else if (val instanceof Number) {
            stmt.setDouble(index, (Double) val);
        } else if (val instanceof String) {
            stmt.setString(index, (String) val);
        } else {
            stmt.setObject(index, val);
        }
    }


    @Override
    public void createTable(final String tablename, final String nameOfPrimaryField, final Collection<String> fieldnames, final Map<String, String> types, final Collection<String> keys) throws SQLException {
        this.checkConnection();
        Objects.requireNonNull(this.connect);
        StringBuilder sqlMainTable = new StringBuilder();
        StringBuilder keyFieldsSB = new StringBuilder();
        sqlMainTable.append("CREATE TABLE IF NOT EXISTS `" + tablename + "` (");
        sqlMainTable.append("`" + nameOfPrimaryField + "` " + types.get(nameOfPrimaryField) + " NOT NULL AUTO_INCREMENT,");
        for (String key : fieldnames) {
            sqlMainTable.append("`" + key + "` " + types.get(key) + " NOT NULL,");
            keyFieldsSB.append("`" + key + "`,");
        }
        sqlMainTable.append("PRIMARY KEY (`" + nameOfPrimaryField + "`)");
        sqlMainTable.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin");

        /* prepare statement */
        try (Statement stmt = this.connect.getConnection().createStatement()) {
            stmt.execute(sqlMainTable.toString());
        }
    }
}

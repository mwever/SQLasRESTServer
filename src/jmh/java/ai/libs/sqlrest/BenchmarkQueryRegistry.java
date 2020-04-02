package ai.libs.sqlrest;

import ai.libs.sqlrest.model.SQLQuery;

public class BenchmarkQueryRegistry {

    public static final String TIME_NULL_RANDOM_SELECT =
            "SELECT * " +
                    "FROM %s " +
                    "WHERE time_started is null " +
                    "ORDER BY RAND() LIMIT %d";

    public static final String TIME_NULL_SELECT =
            "SELECT * " +
                    "FROM %s " +
                    "WHERE time_started is null " +
                    "LIMIT %d";

    public static final String RANDOM_SELECT =
            "SELECT * " +
                    "FROM %s " +
                    "ORDER BY RAND() LIMIT %d";

    public static final String TIME_NULL_RANDOM_SELECT_JOIN =
            "SELECT * " +
            "FROM %s " +
            "WHERE RAND() < (" +
                "SELECT ((%d/COUNT(*))*10) " +
                "FROM %s " +
                "WHERE time_started is null) " +
            "AND time_started is null " +
            "ORDER BY RAND() LIMIT %d";


    public static final String TIME_NULL_RANDOM_SELECT_SUBQUERY =
            "SELECT * " +
            "FROM ( " +
                "SELECT * " +
                "FROM %s " +
                "WHERE time_started is null) AS sub " +
            "ORDER BY RAND() LIMIT %d";

    public static final String SELECT_N =
                    "SELECT * " +
                    "FROM %s " +
                    "LIMIT %d";

    public static String createQuery(String query, String tableName) {
        String sqlQuery;
        switch (query) {
            case "1-random-time-null":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT, tableName, 1);
                break;
            case "10-random-time-null":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT, tableName, 10);
                break;
            case "100-random-time-null":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT, tableName, 100);
                break;
            case "1000-random-time-null":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT, tableName, 1000);
                break;


            case "1-time-null":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_SELECT, tableName, 1);
                break;
            case "10-time-null":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_SELECT, tableName, 10);
                break;
            case "100-time-null":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_SELECT, tableName, 100);
                break;
            case "1000-time-null":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_SELECT, tableName, 1000);
                break;


            case "1-random":
                sqlQuery = String.format(BenchmarkQueryRegistry.RANDOM_SELECT, tableName, 1);
                break;
            case "10-random":
                sqlQuery = String.format(BenchmarkQueryRegistry.RANDOM_SELECT, tableName, 10);
                break;
            case "100-random":
                sqlQuery = String.format(BenchmarkQueryRegistry.RANDOM_SELECT, tableName, 100);
                break;
            case "1000-random":
                sqlQuery = String.format(BenchmarkQueryRegistry.RANDOM_SELECT, tableName, 1000);
                break;


            case "1-random-time-null-join":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT_JOIN, tableName, 1, tableName, 1);
                break;
            case "10-random-time-null-join":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT_JOIN, tableName, 10, tableName, 10);
                break;
            case "100-random-time-null-join":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT_JOIN, tableName, 100, tableName, 100);
                break;
            case "1000-random-time-null-join":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT_JOIN, tableName, 1000,
                        tableName, 1000);
                break;


            case "1-random-time-null-subquery":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT_SUBQUERY, tableName, 1);
                break;
            case "10-random-time-null-subquery":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT_SUBQUERY, tableName, 10);
                break;
            case "100-random-time-null-subquery":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT_SUBQUERY, tableName, 100);
                break;
            case "1000-random-time-null-subquery":
                sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT_SUBQUERY, tableName, 1000);
                break;


            case "select-1":
                sqlQuery = String.format(BenchmarkQueryRegistry.SELECT_N, tableName, 1);
                break;
            case "select-100":
                sqlQuery = String.format(BenchmarkQueryRegistry.SELECT_N, tableName, 100);
                break;
            case "select-1000":
                sqlQuery = String.format(BenchmarkQueryRegistry.SELECT_N, tableName, 1000);
                break;
            case "select-10000":
                sqlQuery = String.format(BenchmarkQueryRegistry.SELECT_N, tableName, 10000);
                break;
            case "select-100000":
                sqlQuery = String.format(BenchmarkQueryRegistry.SELECT_N, tableName, 100000);
                break;
            default:
                throw new IllegalStateException("Query not recognized: " + query);
        }
        return sqlQuery;
    }
}

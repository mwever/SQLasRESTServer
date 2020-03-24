package ai.libs.sqlrest;

public class BenchmarkQueryRegistry {

    public static final String TIME_NULL_RANDOM_SELECT =
                    "SELECT * " +
                    "FROM %s " +
                    "WHERE time_started is null " +
                    "ORDER BY RAND() LIMIT %d";

    public static final String SELECT_N =
                    "SELECT * " +
                    "FROM %s " +
                    "LIMIT %d";
}

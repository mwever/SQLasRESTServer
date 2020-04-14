package ai.libs.sqlrest;

import ai.libs.sqlrest.model.SQLQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomReadQueryBatch {

    private Random random;

    private List<SQLQuery> queryList;

    public RandomReadQueryBatch(int jobsCount, int seed) {
        random = new Random(seed);
        this.queryList = new ArrayList<>(jobsCount);
        for (int i = 0; i < jobsCount; i++) {
            generate();
        }
    }

    private void generate() {
        if(random.nextDouble() < 0.5) {
            generateSelectN();
        } else {
            generateSelectById();
        }
    }

    private void generateSelectN() {
        int rows = Math.abs(random.nextInt() % 100);
        int database = Math.abs(random.nextInt() % 10);
        int table = Math.abs(random.nextInt() % 10);
        SQLQuery query = new SQLQuery(String.format("token_d%d", database),
                BenchmarkQueryRegistry.createSelectNRowsQuery(rows, table));
        queryList.add(query);
    }

    private void generateSelectById() {
        int id = Math.abs(random.nextInt() % 55000);
        int database = Math.abs(random.nextInt() % 10);
        int table = Math.abs(random.nextInt() % 10);
        SQLQuery query = new SQLQuery(String.format("token_d%d", database),
                BenchmarkQueryRegistry.createSelectRowByIdQuery(id, table));
        queryList.add(query);
    }

    public List<SQLQuery> queries() {
        return queryList;
    }


}

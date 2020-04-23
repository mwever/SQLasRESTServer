package ai.libs.sqlrest;

import ai.libs.sqlrest.model.SQLQuery;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class RandomWRUQueryBatch {

    private Random random;

    private List<WRUQueries> queryList;

    public RandomWRUQueryBatch(int jobsCount, int seed) {
        random = new Random(seed);
        this.queryList = new ArrayList<>(jobsCount);
        for (int i = 0; i < jobsCount/3; i++) {
            generate();
        }
    }

    private void generate() {
        int database = Math.abs(random.nextInt() % 10);
        String queryWrite = generateWrite();
        String queryRead = generateSelectById();
        String queryUpdate = generateUpdate();
        WRUQueries wruQueries = new WRUQueries(String.format("token_d%d", database),
                queryWrite, queryRead, queryUpdate);
        queryList.add(wruQueries);
    }

    private String randomText() {
        int length = random.nextInt(100);
        if(length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        for (int i = 0; i < length; i++) {
            int randomLimitedInt = leftLimit + random.nextInt(rightLimit - leftLimit + 1);
            builder.append((char) randomLimitedInt);
        }
        return builder.toString();
    }

    private String randomDate() {
        long stamp = Math.abs(random.nextLong()) % System.currentTimeMillis();
        Date d = new Date(stamp);
        return new SimpleDateFormat("yyyy-MM-dd").format(d);
    }

    private String randomStamp() {
        long stamp = Math.abs(random.nextLong()) % System.currentTimeMillis();
        Date d = new Date(stamp);
        return new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(d);
    }

    private String generateWrite() {
        StringBuilder sqlInsert = new StringBuilder().append("INSERT INTO `twrite` \n")
                .append("(`c1`, `c2`, `c3`, `c4`, `c5`, `c6`, `c7`, `c8`, `c9`, `c10`, `c11`, `c12`, `c13`, `c14`, `c15`, `c16`, `c17`, `c18`, `c19`, `c20`, `c21`, `c22`, `c23`, `c24`, `c25`, `c26`, `c27`, `c28`, `c29`, `c30`, `c31`, `c32`, `c33`, `c34`, `c35`, `c36`, `c37`, `c38`, `c39`, `c40`\n")
                .append(") \n")
                .append("VALUES (");
        for (int i = 1; i <= 40; i++) {
            if(i%4==0) {
                sqlInsert.append("'").append(randomStamp()).append("'");
            }
            else if(i % 4 == 1) {
                sqlInsert.append("'").append(randomText()).append("'");
            }
            else if(i % 4 == 2) {
                int randomNr = random.nextInt(100000);
                sqlInsert.append("'").append(randomNr).append("'");
            }
            else {
                sqlInsert.append("'").append(randomDate()).append("'");
            }
            if(i < 40) {
                sqlInsert.append(", ");
            }
        }
        sqlInsert.append(")");
        return sqlInsert.toString();
    }

    private String generateSelectById() {
        String table = "twrite";
        return BenchmarkQueryRegistry.createSelectRowByIdQuery("%d", table);
    }

    private String generateUpdate() {
        String query = "UPDATE twrite\n" +
                "SET %s = %s\n" +
                "WHERE id=%s";
        String col = "`c" + ((random.nextInt(10) * 4) + 2) + "`";
        String val = String.valueOf(random.nextInt(10000));
        return String.format(query, col, val, "%d");
    }

    public List<WRUQueries> queries() {
        return queryList;
    }

    public String dumpQueries() {
        StringBuilder builder = new StringBuilder();
        queryList.forEach(q -> {
            builder.append("Token: ").append(q.token).append("\n");
            builder.append("Write: ").append(q.write).append("\n");
            builder.append("Read: ").append(q.read).append("\n");
            builder.append("Update: ").append(q.update).append("\n");
            builder.append("\n");
        });
        return builder.toString();
    }

    public static class WRUQueries {
        String token;
        String write, read, update;
        int index = -1;

        public WRUQueries(String token, String write, String read, String update) {
            this.token = token;
            this.write = write;
            this.read = read;
            this.update = update;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public SQLQuery getWrite() {
            return new SQLQuery(token, write);
        }
        public SQLQuery getRead() {
            if(index == -1){
                throw new IllegalStateException();
            }
            return new SQLQuery(token, String.format(read, index));
        }
        public SQLQuery getUpdate() {
            if(index == -1){
                throw new IllegalStateException();
            }
            return new SQLQuery(token, String.format(update, index));
        }


        public String getToken() {
            return token;
        }
    }
}

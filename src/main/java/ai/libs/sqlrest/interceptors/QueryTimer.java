package ai.libs.sqlrest.interceptors;

import ai.libs.sqlrest.ClosableQuery;
import ai.libs.sqlrest.model.SQLQuery;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class QueryTimer implements ClosableQuery.ConnectionCloseHook {

    private final AtomicBoolean finished = new AtomicBoolean(false);

    private final SQLQuery query;

    private final long threshold;

    private final long timeStarted;

    private Optional<Long> finishedTime = Optional.empty();

    private int id = -1;
    private Consumer<Long> runtimeCons;

    public QueryTimer(SQLQuery query, long timeoutThreshold) {
        this.query = query;
        this.threshold = timeoutThreshold;
        this.timeStarted = System.currentTimeMillis();
    }

    void setFinished() {
        finished.set(true);
        finishedTime = Optional.of(System.currentTimeMillis());
        if(runtimeCons != null)
            runtimeCons.accept(getExecTime());
    }


    @Override
    public void action(ClosableQuery access) throws SQLException {
        setFinished();
        synchronized (this) {
            this.notifyAll();
        }
    }

    public boolean isTimedOut() {
        if(isFinished()) {
            return threshold < getExecTime();
        } else {
            return getRemainingMillis() <= 0;
        }
    }

    public boolean isFinished() {
        return finished.get();
    }

    public long getFinishedTime() {
        return finishedTime.get();
    }

    public long getExecTime() {
        return finishedTime.get() - timeStarted;
    }

    public long getRemainingMillis() {
        return threshold - (System.currentTimeMillis() - timeStarted);
    }

    public SQLQuery getQuery() {
        return query;
    }

    public long getTimeStarted() {
        return timeStarted;
    }

    public long getThreshold() {
        return threshold;
    }

    @Override
    public String toString() {
        return "QueryTimer{" +
                "finished=" + finished +
                ", query=" + query +
                ", threshold=" + threshold +
                ", timeStarted=" + timeStarted +
                ", finishedTime=" + finishedTime +
                '}';
    }

    public int getId() {
        return id;
    }

    public void setId(int i) {
        this.id = i;
    }


    public void setFinishHook(Consumer<Long> runtimeCons) {
        this.runtimeCons = runtimeCons;
    }
}

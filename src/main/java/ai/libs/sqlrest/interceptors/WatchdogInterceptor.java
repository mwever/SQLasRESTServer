package ai.libs.sqlrest.interceptors;

import ai.libs.sqlrest.*;
import ai.libs.sqlrest.model.SQLQuery;
import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class WatchdogInterceptor implements IQueryInterceptor, Runnable {

    private final static Logger logger = LoggerFactory.getLogger(WatchdogInterceptor.class);

    private final static IServerConfig CONFIG = ConfigCache.getOrCreate(IServerConfig.class);


    // dependencies
    private final IQueryInterceptor prevInterceptor;

    private final DBQueryLogger dbQueryLogger;

    private final QueryRuntimeModel queryRuntimeModel;

    // internal fields
    private Thread watchDogThread;

    private final BlockingDeque<QueryTimer> queryTimers = new LinkedBlockingDeque<>();

    private final AtomicBoolean isActive = new AtomicBoolean(false);

    private final AtomicLong localTimeSamplesCountCache = new AtomicLong(0);

    private final AtomicLong dynamicThresholdCache = new AtomicLong(0);

    private final double slowestQuantile = CONFIG.slowestQueriesQuantile();

    private final long slowestStaticThreshold = CONFIG.slowQueryThreshold() <= 0 ? 1 : CONFIG.slowQueryThreshold();

    private final long dynamicThresholdLimit = CONFIG.slowQueryDynamicMinLimit() <= 0 ? 1 : CONFIG.slowQueryDynamicMinLimit();

    private final boolean dynamicThreshold = CONFIG.isQueryThresholdDynamic();

    public WatchdogInterceptor(IQueryInterceptor prevInterceptor, DBQueryLogger dbLogger, QueryRuntimeModel queryRuntimeModel) {
        this.prevInterceptor = prevInterceptor;
        this.dbQueryLogger = dbLogger;
        this.queryRuntimeModel = queryRuntimeModel;
    }

    public synchronized void startWatchdog() {
        if(watchDogThread != null && watchDogThread.isAlive()) {
            throw new IllegalStateException("A watchdog thread is already running. Cannot start a second one.");
        }
        watchDogThread = new Thread(this);
        watchDogThread.setDaemon(true);
        watchDogThread.setName("SQL-Query-Watchdog");
        isActive.set(true);
        watchDogThread.start();
        logger.info("The SQL query watchdog thread was started. Subsequent sql queries are being watched and logged if necessary.");
    }

    public synchronized void stopWatchDog()  {
        isActive.set(false);
        logger.info("New queries aren't being logged anymore.");
        if(watchDogThread != null && watchDogThread.isAlive()) {
            watchDogThread.interrupt();
            queryTimers.clear();
            try {
                watchDogThread.join(2000);
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for the watch dog thread to die.");
            }
            if(watchDogThread.isAlive()) {
                throw new RuntimeException("Couldn't destroy the watch dog. Logging for new queries has been disabled though.");
            }
        }
    }

    @Override
    public ClosableQuery requestConnection(SQLQuery query) throws SQLException, InterruptedException {
        ClosableQuery connection = prevInterceptor.requestConnection(query);
        if(isActive.get()) {
            connection.addCloseHook(createTimeout(query));
        }
        return connection;
    }

    private long getThreshold() {
        if(!dynamicThreshold) {
            return slowestStaticThreshold;
        }
        long sampleCount = queryRuntimeModel.getSampleCount();
        if(sampleCount < 50) {
            return slowestStaticThreshold; // not enough samples to create a lower threshold.
        }
        long localSampleCount = localTimeSamplesCountCache.get();
        if(localSampleCount + 100 < sampleCount) {
            boolean controllerThread = localTimeSamplesCountCache.compareAndSet(localSampleCount, sampleCount);
            if(controllerThread) {
                synchronized (this) {
                    dynamicThresholdCache.set((long) queryRuntimeModel.getQueryTime(slowestQuantile));
                }
            }
        }
        long dynThreshold = dynamicThresholdCache.get();
        if(dynThreshold < dynamicThresholdLimit) {
            dynThreshold = dynamicThresholdLimit;
        }
        return Math.min(dynThreshold, slowestStaticThreshold);
    }

    private QueryTimer createTimeout(SQLQuery query) {
        QueryTimer queryTimer = new QueryTimer(query, getThreshold());
        queryTimers.addLast(queryTimer);
        logger.trace("Created a timer for the query `{}`, timer: {}", query, queryTimer);
        return queryTimer;
    }

    @Override
    public void run() {
        try{
            while(isActive.get()) {
                watchLoop();
            }
        }catch(Throwable ex) {
            logger.error("The SQL query watchdog had an exception. The service is terminated and queries aren't logged anymore.", ex);
            isActive.set(false);

        }
    }

    private void watchLoop() throws InterruptedException {
        QueryTimer queryTimer = queryTimers.takeFirst();
        synchronized (queryTimer) {
            while (!queryTimer.isFinished() && !queryTimer.isTimedOut()) {
                long remainingTime = queryTimer.getRemainingMillis();
                if(remainingTime <= 0) {
                    continue;
                }
                queryTimer.wait(remainingTime);
            }
        }
        if(queryTimer.isFinished() && !queryTimer.isTimedOut()) {
            logger.trace("Query {} finished before timeout. Remaining milli seconds: {}", queryTimer.getQuery(), queryTimer.getRemainingMillis());
        }
        if(queryTimer.isTimedOut()) {
            if(!queryTimer.isLogged()) {
                int requestCount = queryTimers.size();
                int unfinishedCount = (int) queryTimers.stream().filter(t -> !t.isFinished()).count();
                logTimeout(queryTimer, requestCount, unfinishedCount);
            } else if(!queryTimer.isExecTimeLogged()) {
                logFinishedUpdate(queryTimer);
            } else if(logger.isTraceEnabled()) {
                logger.trace("Query {} has been logged with exec time but found in the working queue..", queryTimer);
            }
        }
    }

    private void logFinishedUpdate(QueryTimer timer) {
        try {
            dbQueryLogger.updateFinished(timer.getId(), timer.getFinishedTime() - timer.getTimeStarted());
            timer.setExecTimeLogged();
            logger.trace("Updated execution time of {}.", timer);
        } catch (Exception e) {
            logger.error("Couldn't update exec time of {}.", timer, e);
        }
    }

    private void logTimeout(QueryTimer queryTimer, int requestCount, int unfinishedCount) {
        String execTime = null;
        if(queryTimer.isFinished()) {
            execTime = String.valueOf(queryTimer.getExecTime());
        }
        try {
            int id = dbQueryLogger.logTimeOut(queryTimer.getQuery(), queryTimer.getTimeStarted(), queryTimer.getThreshold(), execTime, requestCount, unfinishedCount);
            queryTimer.setId(id);
            queryTimer.setLogged();
            if(execTime != null) {
                queryTimer.setExecTimeLogged();
            }
            logger.trace("Logged timed-out Timer: {}", queryTimer);
        } catch (Exception e) {
            logger.error("Error logging timeout: {}", queryTimer, e);
        }
    }


    class QueryTimer implements ClosableQuery.ConnectionCloseHook {


        private final SQLQuery query;

        private final long threshold;

        private final long timeStarted;

        private long finishedTime = -1;

        private int id = -1;

        private volatile boolean finished = false;

        private boolean isLogged = false;

        private boolean isExecTimeLogged = false;

        public QueryTimer(SQLQuery query, long timeoutThreshold) {
            this.query = query;
            this.threshold = timeoutThreshold;
            this.timeStarted = System.currentTimeMillis();
        }

        void setFinished() {
            finished = true;
            finishedTime = System.currentTimeMillis();
            if(isTimedOut()) {
                queryTimers.addLast(this);
            }
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
            return finished;
        }

        public long getFinishedTime() {
            if(!isFinished()) {
                throw new IllegalStateException();
            }
            return finishedTime;
        }

        public long getExecTime() {
            if(!isFinished()) {
                throw new IllegalStateException();
            }
            return finishedTime - timeStarted;
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

        public boolean isLogged() {
            return isLogged;
        }

        public void setLogged() {
            isLogged = true;
        }

        public boolean isExecTimeLogged() {
            return isExecTimeLogged;
        }

        public void setExecTimeLogged() {
            isExecTimeLogged = true;
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

    }
}

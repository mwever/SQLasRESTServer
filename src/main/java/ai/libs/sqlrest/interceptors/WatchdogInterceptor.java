package ai.libs.sqlrest.interceptors;

import ai.libs.sqlrest.ClosableQuery;
import ai.libs.sqlrest.DBQueryLogger;
import ai.libs.sqlrest.IQueryInterceptor;
import ai.libs.sqlrest.IServerConfig;
import ai.libs.sqlrest.model.SQLQuery;
import com.tdunning.math.stats.TDigest;
import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.text.html.Option;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WatchdogInterceptor implements IQueryInterceptor, Runnable {

    private final static Logger logger = LoggerFactory.getLogger(WatchdogInterceptor.class);

    private final static IServerConfig CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    private final IQueryInterceptor prevInterceptor;

    private final DBQueryLogger dbQueryLogger;

    private Thread watchDogThread;

    private final BlockingDeque<QueryTimer> queryTimers = new LinkedBlockingDeque<>();

    private final List<QueryTimer> toBeUpdated = new ArrayList<>();

    private final AtomicBoolean isActive = new AtomicBoolean(false);

    private final Supplier<Optional<Long>> dynThSupplier;
    private final Consumer<Long> runtimeDigest;

    public WatchdogInterceptor(IQueryInterceptor prevInterceptor, DBQueryLogger dbLogger) {
        this.prevInterceptor = prevInterceptor;
        this.dbQueryLogger = dbLogger;
        if(CONFIG.isQueryThresholdDynamic()) {
            final TDigest tDigest = TDigest.createDigest(500);
            final AtomicInteger sampleSize = new AtomicInteger(0);
            final double quantile = CONFIG.slowestQueriesQuantile();
            final long minDynTh = CONFIG.slowQueryDynamicMinLimit();
            dynThSupplier = () -> {
                int i = sampleSize.incrementAndGet();
                if(i > 500) {
                    double dynThreshold = tDigest.quantile(quantile);
                    if(dynThreshold < minDynTh) {
                        return Optional.of(minDynTh);
                    } else {
                        return Optional.of(minDynTh);
                    }
                }
                return Optional.empty();
            };
            runtimeDigest = x -> {
                sampleSize.incrementAndGet();
                tDigest.add(x);
            };
        } else {
            runtimeDigest = l -> {};
            dynThSupplier = Optional::empty;
        }
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
        long t = CONFIG.slowQueryThreshold();
        if(t < 0) {
            logger.warn("Threshold is negative: {}. Defaulting to 0", t);
            t = 0;
        }
        Optional<Long> dynThOpt = dynThSupplier.get();
        if(dynThOpt.isPresent()) {
            long dynTh = dynThOpt.get();
            if(dynTh < t && dynTh > 0) {
                t = dynTh;
            }
        }
        return t;
    }

    private QueryTimer createTimeout(SQLQuery query) {
        QueryTimer queryTimer = new QueryTimer(query, getThreshold());
        queryTimers.addLast(queryTimer);
        queryTimer.setFinishHook(runtimeDigest);
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
        if(queryTimers.peekFirst() == null) {
            // nothing is in queue. Write some updates:
            writeUpdates();
        }
        QueryTimer queryTimer = queryTimers.takeFirst();
        synchronized (queryTimer) {
            while (!queryTimer.isFinished() && !queryTimer.isTimedOut()) {
                writeUpdates(); // instead of waiting write some updates
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
            int requestCount = queryTimers.size();
            int unfinishedCount = (int) queryTimers.stream().filter(t -> !t.isFinished()).count();
            logTimeout(queryTimer, requestCount, unfinishedCount);
        }
    }

    private void writeUpdates() {
        Iterator<QueryTimer> iterator = toBeUpdated.iterator();
        QueryTimer update = null;
        while(iterator.hasNext()) {
            QueryTimer next;
            next = iterator.next();
            if(next.isFinished()) {
                iterator.remove();
                update = next;
                break;
            }
        }
        if(update != null) {
            try {
                dbQueryLogger.updateFinished(update.getId(), update.getFinishedTime() - update.getTimeStarted());
                logger.trace("Updated execution time of {}.", update);
            } catch (Exception e) {
                logger.error("Couldn't update exec time of {}.", update, e);
            }
        }
    }

    private void logTimeout(QueryTimer queryTimer, int requestCount, int unfinishedCount) {
        try {
            int id = dbQueryLogger.logTimeOut(queryTimer.getQuery(), queryTimer.getTimeStarted(), queryTimer.getThreshold(), requestCount, unfinishedCount);
            queryTimer.setId(id);
            toBeUpdated.add(queryTimer);
            logger.trace("Logged timed-out Timer: {}", queryTimer);
        } catch (Exception e) {
            logger.error("Error logging timeout: {}", queryTimer, e);
        }
    }

}

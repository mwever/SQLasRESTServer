package ai.libs.sqlrest;

import ai.libs.jaicore.timing.TimedObjectEvaluator;
import com.tdunning.math.stats.TDigest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class QueryRuntimeModel {

    private TDigest timeDigest = TDigest.createDigest(500);

    private final AtomicLong count = new AtomicLong(0);

    private final double[] STD_QUANTILES = {
      0.005, 0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99, 0.995
    };

    public QueryRuntimeModel() {
    }

    public synchronized void recordQueryTime(long time) {
        timeDigest.add(time);
        count.incrementAndGet();
    }

    public long getSampleCount() {
        return count.get();
    }

    public synchronized void reset() {
        timeDigest = TDigest.createDigest(500);
        count.set(0);
    }

    public synchronized double getQueryTime(double quantile) {
        return timeDigest.quantile(quantile);
    }

    public synchronized Map<String, Double> getQueryTimes() {
        Map<String, Double> queryTimes = new TreeMap<>();
        for (double stdQuantile : STD_QUANTILES) {
            queryTimes.put(String.valueOf(stdQuantile), timeDigest.quantile(stdQuantile));
        }
        queryTimes.put("samples", (double) getSampleCount());
        return queryTimes;
    }

}

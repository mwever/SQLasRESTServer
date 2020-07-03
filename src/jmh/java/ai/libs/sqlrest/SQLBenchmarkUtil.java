package ai.libs.sqlrest;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SQLBenchmarkUtil {

    private static AtomicBoolean WARNED_MISSING_FLUSH_SCRIPT = new AtomicBoolean(false);
    private static AtomicBoolean WARNED_MISSING_PERF_PROPS = new AtomicBoolean(false);

    private final static Logger logger = LoggerFactory.getLogger(SQLBenchmarkUtil.class);

    public static void startService(SQLRestServiceState serviceState, SQLClientState clientState) {
        serviceState.start();
        serviceState.wait(clientState.getWebClient());
    }

    public static void flushDB() {
        File script = new File("benchmark-workingdir/flushdb.sh");
        if(script.exists()) {
            script.setExecutable(true);
            ProcessBuilder pb = new ProcessBuilder(script.getAbsolutePath());
            try {
                Process scriptProcess = pb.start();
                boolean b = scriptProcess.waitFor(3, TimeUnit.SECONDS);
                if(scriptProcess.isAlive()) {
                    scriptProcess.destroy();
                    throw new IllegalStateException("Script did not finish.");
                }
            } catch (Exception e) {
                logger.error("Error executing flush database script: {} ", script.getAbsolutePath(), e);
            }
        } else if(!WARNED_MISSING_FLUSH_SCRIPT.getAndSet(true)) {
            logger.warn("Script to flush database is missing. Place it under: {}", script.getAbsolutePath());
        }
    }

    public static Properties getPerformanceProps() {
        Properties perfProps = new Properties();
        File perfPropFile = new File("conf/server.perf.properties");
        try(FileInputStream fis = new FileInputStream(perfPropFile)) {
            perfProps.load(fis);
        } catch (Exception e) {
            if(!WARNED_MISSING_PERF_PROPS.getAndSet(true)) {
                logger.warn("No performance properties were found or could be read. Place it under: {}", perfPropFile.getAbsolutePath(), e);
            }
            return perfProps;
        }
        return perfProps;
    }


}

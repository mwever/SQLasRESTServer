package ai.libs.sqlrest;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

public final class AbstractServiceBenchmark {

    public static void startService(SQLRestServiceState serviceState, SQLClientState clientState) {
        serviceState.start();
        serviceState.wait(clientState.getWebClient());
    }

}

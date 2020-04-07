package ai.libs.sqlrest;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

public class AbstractServiceBenchmark {

    public void startService(SQLRestServiceState serviceState, SQLClientState clientState) {
        serviceState.start();
        serviceState.wait(clientState.getWebClient());
    }

}

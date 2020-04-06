package ai.libs.sqlrest;

import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@ConditionalOnProperty("ai.libs.sqlrest.benchmark.BenchmarkMode")
public class BenchmarkHelperController {

    private final static Logger logger = LoggerFactory.getLogger(BenchmarkHelperController.class);

    private final IServerConfig config;

    public BenchmarkHelperController(IServerConfig config) {
        this.config = config;
    }

    @PostMapping("/numadapter")
    public void setNumAdapterInstances(@RequestBody final Integer numberOfAdapter) {
        if(numberOfAdapter == null) {
            throw new IllegalArgumentException("Number of adapter instances is undefined");
        }
        if(numberOfAdapter < 1) {
            throw new IllegalArgumentException("Number of adapter instances cannot be smaller than 1. Given: " + numberOfAdapter);
        }
        if(numberOfAdapter > config.getNumAdapterInstancesLimit()) {
            throw new IllegalArgumentException(
                    String.format("Number of adapter instances cannot exceed the defined limit of %d." +
                            " Given: %d", config.getNumAdapterInstancesLimit(), numberOfAdapter));
        }
        config.setProperty(IServerConfig.K_NUM_ADAPTER_INSTANCES, String.valueOf(numberOfAdapter));
        logger.info("Set number of adapters = {}", numberOfAdapter);
    }

    @GetMapping("/numadapter")
    public Integer getNumAdapterInstances() {
        return config.getNumAdapterInstances();
    }


    @GetMapping("/block")
    public void blockRequest() throws InterruptedException {
        Thread.sleep(TimeUnit.HOURS.toMillis(1));
    }

    @GetMapping("/isAlive")
    public Boolean isAlive() {
        return true;
    }

}

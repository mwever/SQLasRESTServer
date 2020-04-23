package ai.libs.sqlrest;

import ai.libs.sqlrest.arbiter.CyclicAdapterArbiter;
import ai.libs.sqlrest.arbiter.LimitedAccessAdapterArbiter;
import ai.libs.sqlrest.arbiter.RandomAdapterArbiter;
import ai.libs.sqlrest.supplier.CustomAdapterSupplier;
import ai.libs.sqlrest.supplier.DefaultAdapterSupplier;
import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SQLServerConfiguration {

    private final static Logger logger = LoggerFactory.getLogger(SQLServerConfiguration.class);

    @Bean
    IAdapterArbiter getAccessImpl(SQLAdapterManager adapterManager) {
        IServerConfig conf = ConfigCache.getOrCreate(IServerConfig.class);
        IAdapterArbiter impl;
        if(conf.isAccessRandom()) {
            logger.info("SQLAccess is random.");
            impl = new RandomAdapterArbiter(adapterManager);
        }
        else {
            logger.info("SQLAccess is cyclic.");
            impl = new CyclicAdapterArbiter(adapterManager);
        }
        if(conf.isAccessLimited()) {
            logger.info("SQLAccess is limited to {}", conf.getNumAdapterAccessLimit());
            IAdapterArbiter limitedAccess = new LimitedAccessAdapterArbiter(impl);
            impl = limitedAccess;
        }
        return impl;
    }

    @Bean
    ISLAdapterSupplier getSQLAdapter() {
        IServerConfig conf = ConfigCache.getOrCreate(IServerConfig.class);
        return new CustomAdapterSupplier();
        // based on the config other implementations are chosen.
    }

}

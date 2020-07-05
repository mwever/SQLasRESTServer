package ai.libs.sqlrest;

import ai.libs.sqlrest.interceptors.*;
import ai.libs.sqlrest.supplier.CustomAdapterSupplier;
import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class SQLServerConfiguration {

    private final static Logger logger = LoggerFactory.getLogger(SQLServerConfiguration.class);

    @Bean
    IQueryInterceptor interceptorConf(ApplicationContext context, SQLAdapterManager adapterManager, QueryRuntimeModel runtimeModel) {
        IServerConfig conf = ConfigCache.getOrCreate(IServerConfig.class);
        IQueryInterceptor impl;
        if(conf.isAccessRandom()) {
            logger.info("SQLAccess is random.");
            impl = new RandomConnectionArbiter(adapterManager);
        }
        else {
            logger.info("SQLAccess is cyclic.");
            impl = new CyclicConnectionArbiter(adapterManager);
        }
        if(conf.isAccessLimited()) {
            logger.info("SQLAccess is limited to {}", conf.getNumAdapterAccessLimit());
            impl = new LimitedAccessConnectionInterceptor(impl);
        }
        impl = new QueryTimeRecorder(impl, runtimeModel);
        if(conf.isLogSlowQueriesEnabled()) {
            logger.info("Added watchdog interceptor with threshold: {}", conf.slowQueryThreshold());
            DBQueryLogger logger = context.getBean(DBQueryLogger.class);
            WatchdogInterceptor watchDog = new WatchdogInterceptor(impl, logger, runtimeModel);
            watchDog.startWatchdog();
            impl = watchDog;
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

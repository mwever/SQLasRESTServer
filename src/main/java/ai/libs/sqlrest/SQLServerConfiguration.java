package ai.libs.sqlrest;

import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SQLServerConfiguration {

    private final static Logger logger = LoggerFactory.getLogger(SQLServerConfiguration.class);

    @Bean
    ISQLAdapterAccess getAccessImpl(SQLAdapterManager adapterManager) {
        IServerConfig conf = ConfigCache.getOrCreate(IServerConfig.class);
        ISQLAdapterAccess impl;
        if(conf.isAccessRandom()) {
            logger.info("SQLAccess is random.");
            impl = new ISQLAdapterRandomAccess(adapterManager);
        }
        else {
            logger.info("SQLAccess is cyclic.");
            impl = new ISQLAdapterCyclicAccess(adapterManager);
        }
        if(conf.isAccessLimited()) {
            logger.info("SQLAccess is limited to {}", conf.getNumAdapterAccessLimit());
            ISQLAdapterAccess limitedAccess = new ISQLAdapterLimitedAccess(impl);
            impl = limitedAccess;
        }
        return impl;
    }

}

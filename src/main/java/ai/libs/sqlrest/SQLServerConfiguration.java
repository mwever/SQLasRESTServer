package ai.libs.sqlrest;

import org.aeonbits.owner.ConfigCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SQLServerConfiguration {

    @Bean
    IServerConfig getServerConfiguration() {
        return ConfigCache.getOrCreate(IServerConfig.class);
    }

}

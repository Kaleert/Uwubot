package pro.kaleert.uwubot.config;

import pro.kaleert.nyagram.core.spi.BaseUrlController;
import pro.kaleert.nyagram.core.spi.NyagramBotConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BaseUrlConfig {

    @Bean
    public BaseUrlController baseUrlController(NyagramBotConfig config) {
        return new BaseUrlController() {
            @Override
            public String getBaseUrl() {
                String url = config.getApiUrl();
                return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            }

            @Override
            public void reportError(String failedUrl, Throwable ex) {
            }
        };
    }
}
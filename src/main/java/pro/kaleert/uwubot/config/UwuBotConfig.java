package pro.kaleert.uwubot.config;

import com.kaleert.nyagram.core.spi.NyagramBotConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@Primary
@ConfigurationProperties(prefix = "nyagram")
public class UwuBotConfig implements NyagramBotConfig {

    private String botToken;
    private String botUsername;
    private Long adminId;
    private BotMode mode = BotMode.POLLING;
    
    private String webhookUrl;
    private String webhookPath = "/callback/telegram";
    private String webhookSecretToken;
    
    private int longPollingTimeoutSeconds = 50;
    private int pollingRetryDelaySeconds = 5;
    private int pollingMaxBackoffSeconds = 60;
    
    private int workerThreadCount = Runtime.getRuntime().availableProcessors() * 2;
    private String workerThreadPrefix = "nyagram-worker-";
    private long workerKeepAliveTimeMs = 60_000L;
    
    private List<String> allowedUpdates;
    private String apiUrl = "https://api.telegram.org";
}

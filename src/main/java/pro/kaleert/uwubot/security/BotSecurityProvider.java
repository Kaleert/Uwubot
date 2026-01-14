package pro.kaleert.uwubot.security;

import com.kaleert.nyagram.api.objects.User;
import com.kaleert.nyagram.security.spi.UserLevelProvider;
import com.kaleert.nyagram.security.spi.UserPermissionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.kaleert.uwubot.config.UwuBotConfig;

import java.util.Collections;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BotSecurityProvider implements UserLevelProvider, UserPermissionProvider {

    private final UwuBotConfig properties;

    @Override
    public Integer getUserLevel(User telegramUser) {
        if (isSuperAdmin(telegramUser)) {
            return 10;
        }
        return 0;
    }

    @Override
    public Set<String> getUserPermissions(User user) {
        return Collections.emptySet();
    }
    
    @Override
    public boolean isSuperAdmin(User user) {
        return properties.getAdminId() != null && properties.getAdminId().equals(user.getId());
    }
}
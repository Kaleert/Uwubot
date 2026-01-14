package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import lombok.RequiredArgsConstructor;

// Value не важен, так как методы будут регистрироваться по алиасам
@BotCommand(value = "/menu_handler")
@RequiredArgsConstructor
public class MenuCommands {

    private final RaspCommand raspCommand;
    private final SettingsCommand settingsCommand;

    @CommandHandler(aliases = "📅 Расписание") 
    public void onRaspButton(CommandContext context) {
        raspCommand.showSchedule(context, null);
    }

    @CommandHandler(aliases = "⚙️ Настройки")
    public void onSettingsButton(CommandContext context) {
        settingsCommand.execute(context);
    }
}
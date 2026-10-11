package pro.kaleert.uwubot.command;

import pro.kaleert.nyagram.command.BotCommand;
import pro.kaleert.nyagram.command.CommandContext;
import pro.kaleert.nyagram.command.CommandHandler;
import lombok.RequiredArgsConstructor;

@BotCommand
@RequiredArgsConstructor
public class MenuCommands {

    private final RaspCommand raspCommand;
    private final SettingsCommand settingsCommand;

    @CommandHandler(value = "📅 Расписание", description = "Показать расписание")
    public void onRaspButton(CommandContext context) {
        raspCommand.showSchedule(context, null);
    }

    @CommandHandler(value = "⚙️ Настройки", description = "Открыть настройки")
    public void onSettingsButton(CommandContext context) {
        settingsCommand.execute(context);
    }
}
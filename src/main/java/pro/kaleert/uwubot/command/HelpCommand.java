package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import com.kaleert.nyagram.util.HelpGenerator;
import lombok.RequiredArgsConstructor;

@BotCommand(value = "/help", description = "Показать эту справку")
@RequiredArgsConstructor
public class HelpCommand {

    private final HelpGenerator helpGenerator;

    @CommandHandler(aliases = {"помощь", "хелп"})
    public void execute(CommandContext context) {
        String helpText = helpGenerator.generate(context.getTelegramUser());
        context.reply(helpText, "HTML");
    }
}
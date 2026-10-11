package pro.kaleert.uwubot.command;

import pro.kaleert.nyagram.command.BotCommand;
import pro.kaleert.nyagram.command.CommandContext;
import pro.kaleert.nyagram.command.CommandHandler;
import pro.kaleert.nyagram.util.HelpGenerator;
import lombok.RequiredArgsConstructor;

@BotCommand(value = "/help", description = "Показать эту справку")
@RequiredArgsConstructor
public class HelpCommand {

    private final HelpGenerator helpGenerator;

    @CommandHandler(aliases = {"помощь", "хелп"})
    public void execute(CommandContext context) {
        String helpText = helpGenerator.generate(context.getTelegramUser());
        
        helpText += "\n➖➖➖➖➖➖➖➖➖➖\n";
        helpText += "<i>Sponsored by <a href=\"https://t.me/vpnka_official_bot?start=utm_rasp\">VPN-KA</a></i>";
        helpText += "<i>Powered by <a href=\"https://github.com/kaleert/nyagram\">Nyagram</a></i>\n";
        
        context.reply(helpText, "HTML");
    }
}
package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.security.BotSecurityProvider;

@BotCommand(value = "/help", description = "Помощь")
@RequiredArgsConstructor
public class HelpCommand {

    private final BotSecurityProvider securityProvider;

    @CommandHandler(aliases = {"помощь", "хелп"})
    public void execute(CommandContext context) {
        StringBuilder sb = new StringBuilder("🎓 <b>Справка по боту</b>\n\n");
        
        sb.append("📅 <b>Расписание:</b>\n");
        sb.append("<code>/rasp</code> — расписание вашей группы\n");
        sb.append("<code>/rasp [группа]</code> — расписание другой группы\n");
        sb.append("<code>/bells</code> — расписание звонков\n\n");
        
        sb.append("⚙️ <b>Настройки:</b>\n");
        sb.append("<code>/group [группа]</code> — выбрать свою группу\n");
        sb.append("<code>/settings</code> — меню настроек (алиасы, коды)\n");
        sb.append("<code>/alias [предмет]=[имя]</code> — переименовать предмет\n\n");
        
        if (securityProvider.isSuperAdmin(context.getTelegramUser())) {
            sb.append("🛡 <b>Админ-панель:</b>\n");
            sb.append("<code>/stats</code> — статистика\n");
            sb.append("<code>/test parser [url]</code> — тест парсера\n");
            sb.append("<code>/test broadcast [url]</code> — тест рассылки\n");
        }

        context.reply(sb.toString(), "HTML");
    }
}

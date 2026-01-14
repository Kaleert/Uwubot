package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.command.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.SubjectAlias;
import pro.kaleert.uwubot.repository.SubjectAliasRepository;

import java.util.List;
import java.util.Optional;

@BotCommand(value = "/alias", description = "Переименовать предмет")
@RequiredArgsConstructor
public class AliasCommand {

    private final SubjectAliasRepository aliasRepository;

    @CommandHandler(aliases = {"алиас", "замена"})
    @Transactional
    public void execute(CommandContext context, @CommandArgument(value = "args", required = false) String args) {
        Long userId = context.getUserId();

        if (args == null || args.isBlank()) {
            List<SubjectAlias> list = aliasRepository.findAllByUserId(userId);
            if (list.isEmpty()) {
                context.reply("📝 У вас нет алиасов.\nИспользование: <code>/alias Старое Имя = Новое Имя</code>\nПример: <code>/alias Математика = Матеша</code>", "HTML");
            } else {
                StringBuilder sb = new StringBuilder("<b>Ваши замены:</b>\n");
                for (SubjectAlias a : list) {
                    sb.append("• ").append(a.getOriginalName()).append(" ➝ <b>").append(a.getAliasName()).append("</b>\n");
                }
                sb.append("\nДля удаления: <code>/alias remove [Имя]</code>\nДобавление: <code>/alias Старое Имя = Новое Имя</code>");
                context.reply(sb.toString(), "HTML");
            }
            return;
        }

        if (args.toLowerCase().startsWith("remove ")) {
            String toRemove = args.substring(7).trim();
            aliasRepository.deleteByUserIdAndOriginalName(userId, toRemove);
            context.reply("🗑 Алиас для '" + toRemove + "' удален.");
            return;
        }

        if (!args.contains("=")) {
            context.reply("❌ Неверный формат. Используйте знак '='.\nПример: <code>/alias Физика = Физра</code>", "HTML");
            return;
        }

        String[] parts = args.split("=", 2);
        String original = parts[0].trim();
        String alias = parts[1].trim();

        if (original.length() < 2 || alias.length() < 2) {
            context.reply("⚠️ Названия слишком короткие.");
            return;
        }

        Optional<SubjectAlias> existing = aliasRepository.findByUserIdAndOriginalName(userId, original);
        SubjectAlias sa = existing.orElse(new SubjectAlias(userId, original, alias));
        sa.setAliasName(alias);
        
        aliasRepository.save(sa);
        
        context.reply("✅ Готово! Теперь <b>" + original + "</b> будет отображаться как <b>" + alias + "</b>.", "HTML");
    }
}
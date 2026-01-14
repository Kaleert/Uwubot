package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.ParsingMeta;
import pro.kaleert.uwubot.repository.ParsingMetaRepository;

@BotCommand(value = "/bells", description = "Расписание звонков")
@RequiredArgsConstructor
public class BellsCommand {

    private final ParsingMetaRepository metaRepository;

    @CommandHandler(aliases = {"звонки", "время"})
    public void execute(CommandContext context) {
        ParsingMeta meta = metaRepository.findById("schedule_file").orElse(null);
        
        if (meta == null || meta.getLastBellSchedule() == null || meta.getLastBellSchedule().isBlank()) {
            context.reply("⚠️ Информация о звонках пока не загружена.\nПопробуйте позже (после обновления файла).", "HTML");
            return;
        }

        String msg = "🔔 <b>Расписание звонков:</b>\n\n" + meta.getLastBellSchedule();
        context.reply(msg, "HTML");
    }
}

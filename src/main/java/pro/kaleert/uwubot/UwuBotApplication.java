package pro.kaleert.uwubot;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan; // <--- Добавь импорт
import org.springframework.scheduling.annotation.EnableScheduling;
import pro.kaleert.uwubot.config.ConfigInitializer;

@EnableScheduling
@SpringBootApplication
@ComponentScan(basePackages = {"pro.kaleert.uwubot", "com.kaleert.nyagram"})
public class UwuBotApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(UwuBotApplication.class)
                .initializers(new ConfigInitializer())
                .run(args);
    }
}
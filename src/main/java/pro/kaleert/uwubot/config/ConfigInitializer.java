package pro.kaleert.uwubot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Slf4j
public class ConfigInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String CONFIG_FILENAME = "config.yml";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        File configFile = new File(System.getProperty("user.dir"), CONFIG_FILENAME);

        if (!configFile.exists()) {
            log.warn("Config file '{}' not found. Creating default from resources... Please configure it and restart the app.", CONFIG_FILENAME);
            copyConfigFromResources(configFile);
            System.exit(0);
        } else {
            log.info("Found existing configuration file: {}", configFile.getAbsolutePath());
        }

        if (configFile.exists()) {
            loadExternalConfig(applicationContext, configFile);
        }
    }

    private void copyConfigFromResources(File destination) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILENAME)) {
            if (stream == null) {
                log.error("Default {} not found in classpath! Application might fail.", CONFIG_FILENAME);
                return;
            }
            Files.copy(stream, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("Default config created successfully: {}", destination.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create default config file", e);
        }
    }

    private void loadExternalConfig(ConfigurableApplicationContext context, File file) {
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            Resource fileResource = new FileSystemResource(file);
            List<PropertySource<?>> sources = loader.load("external-yaml-config", fileResource);

            if (!sources.isEmpty()) {
                context.getEnvironment().getPropertySources().addFirst(sources.get(0));
                log.info("Loaded external configuration from {}", file.getName());
            }
        } catch (IOException e) {
            log.error("Failed to load external config file", e);
            throw new RuntimeException("Could not load config.yml", e);
        }
    }
}

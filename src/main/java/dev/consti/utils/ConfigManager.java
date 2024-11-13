package dev.consti.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import dev.consti.logging.Logger;

/**
 * ConfigManager is a concrete class for managing configuration data stored in YAML files.
 */
public class ConfigManager {

    private Map<String, Map<String, Object>> configData = new HashMap<>();
    private String secret;
    private final Yaml yaml;
    private final Logger logger;
    private final String configDirectory; // Configurable directory for configuration files
    private final String secretFileName; // Configurable secret file name
    
    /**
     * Construtor with default secret file name.
     *
     * @param logger              A Logger instance for logging
     * @param pluginName     The name of the plugin (used for directory namimg)
     */
    public ConfigManager(Logger logger, String pluginName) {
        this(logger, pluginName, "secret.key");
    }
    /**
     * Constructor for ConfigManager, which takes a Logger, the configuration directory, 
     * and the secret file name.
     * 
     * @param logger              A Logger instance for logging
     * @param pluginName     The name of the plugin (used for directory namimg)
     * @param secretFileName The default name of the secret file
     */
    public ConfigManager(Logger logger, String pluginName, String secretFileName) {
        this.logger = logger;
        this.configDirectory = "plugins" + File.separator + (pluginName != null ? pluginName : "FoundationLib");
        this.secretFileName = secretFileName;

        // Set YAML configuration options
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yaml = new Yaml(options);
    }

    /**
     * Loads configuration data from the specified file.
     * If the file does not exist, it is copied from a default resource.
     * 
     * @param fileName        The name of configuration file in the resources directory
     * @param targetFileName  The target name for the configuration file
     */
    public void loadConfig(String fileName, String targetFileName) {
        File configFile = new File(configDirectory, targetFileName);

        if (!configFile.exists()) {
            copyConfig(fileName, targetFileName);
        }

        // Load the config file
        try (InputStream inputStream = Files.newInputStream(configFile.toPath())) {
            Map<String, Object> fileconfigData = yaml.load(inputStream);
            if (fileconfigData == null) {
                fileconfigData = new HashMap<>();
            }
            configData.put(targetFileName, fileconfigData);
            logger.info("Config file loaded successfully from path: {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to load config file: {}", e.getMessage());
        }
    }

    /**
     * Loads the secret from the specified file.
     * If the file does not exist, it is generated.
     * 
     * 
     */
    public void loadSecret() {
        File secretFile = new File(secretFileName);

        if (!secretFile.exists()) {
            generateSecret();
        }

        // Load the secret file
        try (InputStream inputStream = Files.newInputStream(secretFile.toPath())) {
            secret = new String(inputStream.readAllBytes());
            logger.info("Secret file loaded successfully from path: {}", secretFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to load secret file: {}", e.getMessage());
        }
    }

    /**
     * Retrieves a configuration value based on the specified key.
     * 
     * @param key The key for the configuration value
     * @param fileName The name of the configuration file
     * @return The value as a String, or null if the key does not exist
     */
    public String getKey(String fileName, String key) {
        Map<String, Object> fileConfigData = configData.get(fileName);
        if (fileConfigData != null && fileConfigData.containsKey(key)) {
            logger.debug("Retrieved key '{}' from config '{}'", key, fileName);
            return fileConfigData.get(key).toString();
        } else {
            logger.error("Key '{}' not found in config '{}'", key, fileName);
            return null;
        }
    }

    /**
     * Retrieves the stored secret.
     * 
     * @return The secret as a String, or null if no secret was loaded
     */
    public String getSecret() {
        if (secret != null) {
            logger.debug("Retrieved secret");
            return secret;
        } else {
            logger.error("Secret not found");
            return null;
        }
    }

    /**
     * Generates a new secret file if it does not exist.
     * 
     * 
     */
    protected void generateSecret() {
        File configDir = new File(configDirectory);
        if (!configDir.exists() && !configDir.mkdirs()) {
            logger.error("Failed to create config directory: {}", configDirectory);
            return;
        }

        File secretFile = new File(configDir, secretFileName);

        // Generate the secret file
        try (OutputStream out = Files.newOutputStream(secretFile.toPath())) {
            String secret = TLSUtils.generateSecret(); 
            out.write(secret.getBytes());
            logger.info("Secret file generated successfully at: {}", secretFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to generate secret file: {}", e.getMessage());
        }
    }

    /**
     * Copies a default configuration file from resources to the target directory.
     * 
     * @param fileName       The name of the default resource
     * @param targetFileName The target name for the configuration file
     */
    protected void copyConfig(String fileName, String targetFileName) {
        File configDir = new File(configDirectory);
        if (!configDir.exists() && !configDir.mkdirs()) {
            logger.error("Failed to create config directory: {}", configDirectory);
            return;
        }

        File configFile = new File(configDir, targetFileName);

        // Copy config file from resources
        try (InputStream in = getClass().getResourceAsStream("/" + fileName);
             OutputStream out = Files.newOutputStream(configFile.toPath())) {
            if (in == null) {
                logger.error("Resource {} not found in the library JAR.", fileName);
                return;
            }

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            logger.info("Default {} copied to: {}", fileName, configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to copy default config file {}: {}", fileName, e.getMessage());
        }
    }
}


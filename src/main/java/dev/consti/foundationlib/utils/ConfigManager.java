package dev.consti.foundationlib.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import dev.consti.foundationlib.logging.Logger;

/**
 * ConfigManager is a concrete class for managing configuration data stored in
 * YAML files.
 */
public class ConfigManager {

    private final Map<String, Map<String, Object>> configData = new HashMap<>();
    private String secret;
    private final Yaml yaml;
    private final Logger logger;
    private final String configDirectory; // Configurable directory for configuration files
    private final String secretFileName; // Configurable secret file name

    /**
     * Constructor with default secret file name.
     *
     * @param logger     A Logger instance for logging
     * @param pluginName The name of the plugin (used for directory naming)
     */
    public ConfigManager(Logger logger, String pluginName) {
        this(logger, pluginName, "secret.key");
    }

    /**
     * Constructor for ConfigManager, which takes a Logger, the configuration
     * directory,
     * and the secret file name.
     * 
     * @param logger         A Logger instance for logging
     * @param pluginName     The name of the plugin (used for directory naming)
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
     * Loads all YAML configuration files in the config directory.
     */
    public void loadAllConfigs() {
        File configDir = new File(configDirectory);
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new RuntimeException("Failed to create config directory: " + configDirectory);
        }

        try {
            Files.list(configDir.toPath())
                    .filter(path -> path.toString().endsWith(".yml"))
                    .forEach(this::loadConfigFile);

            logger.debug("All configuration files have been loaded from directory: {}", configDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration files: " + e.getMessage(), e);
        }
    }

    /**
     * Reloads all configurations and the secret by clearing the current cache
     * and loading all configuration files again.
     */
    public void reload() {
        // Clear current configuration data and reset the secret
        configData.clear();

        // Reload all configuration files in the directory
        loadAllConfigs();

        logger.info("All configurations have been successfully reloaded");
    }

    /**
     * Loads configuration data from the specified file.
     * If the file does not exist, it is copied from a default resource.
     * 
     * @param path The path to the YAML script fil
     */
    private void loadConfigFile(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Map<String, Object> fileconfigData = yaml.load(inputStream);
            if (fileconfigData == null) {
                fileconfigData = new HashMap<>();
            }
            configData.put(path.getFileName().toString(), fileconfigData);
            logger.debug("Config file loaded successfully: {}", path.getFileName().toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config file: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the secret from the specified file.
     * If the file does not exist, it is generated.
     * 
     * 
     */
    public void loadSecret() {
        File secretFile = new File(configDirectory, secretFileName);

        if (!secretFile.exists()) {
            generateSecret();
        }
        // Load the secret file
        try (InputStream inputStream = Files.newInputStream(secretFile.toPath())) {
            secret = new String(inputStream.readAllBytes());
            logger.debug("Secret file loaded successfully from path: {}", secretFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load secret file: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a configuration value based on the specified key.
     * 
     * @param key      The key for the configuration value
     * @param fileName The name of the configuration file
     * @return The value as a String, or null if the key does not exist
     */
    public String getKey(String fileName, String key) {
        Map<String, Object> fileConfigData = configData.get(fileName);
        if (fileConfigData != null && fileConfigData.containsKey(key)) {
            logger.debug("Retrieved key '{}' from config: {}", key, fileName);
            return fileConfigData.get(key).toString();
        } else {
            throw new RuntimeException("Key '" + key + "' not found in config: " + fileName);
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
            throw new RuntimeException("Failed to create config directory: " + configDirectory);
        }

        File secretFile = new File(configDir, secretFileName);

        if (secretFile.exists()) {
            logger.debug("Secret file already exists, skipping copy");
            return;
        }

        // Generate the secret file
        try (OutputStream out = Files.newOutputStream(secretFile.toPath())) {
            String secret = TLSUtils.generateSecret();
            out.write(secret.getBytes());
            logger.info("Secret file generated successfully at: {}", secretFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate secret file: " + e.getMessage(), e);
        }
    }

    /**
     * Copies a default configuration file from resources to the target directory.
     * 
     * @param resourceName   The name of the default resource
     * @param targetFileName The target name for the configuration file
     */
    public void copyConfig(String resourceName, String targetFileName) {
        File configDir = new File(configDirectory);
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new RuntimeException("Failed to create config directory: " + configDirectory);
        }

        File configFile = new File(configDir, targetFileName);

        if (configFile.exists()) {
            logger.debug("Config file '{}' already exists, skipping copy", configFile.getAbsolutePath());
            return;
        }

        // Copy config file from resources
        try (InputStream in = getClass().getResourceAsStream("/" + resourceName);
                OutputStream out = Files.newOutputStream(configFile.toPath())) {
            if (in == null) {
                throw new RuntimeException("Resource '" + resourceName + "' not found in the plugin JAR");
            }

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            logger.info("Default config '{}' copied to: {}", resourceName, configFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy default config file " + resourceName + ": " + e.getMessage(), e);
        }
    }
}

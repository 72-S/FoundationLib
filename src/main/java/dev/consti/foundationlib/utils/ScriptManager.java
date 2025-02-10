package dev.consti.foundationlib.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import dev.consti.foundationlib.logging.Logger;

/**
 * ScriptManager is a concrete class for managing scripts data stored in YAML files.
 */
public abstract class ScriptManager {
    private final Map<String, ScriptConfig> scripts = new HashMap<>();
    private final Yaml yaml;
    private final Logger logger;
    private final String scriptsDirectory;

    /**
     * Initializes the ScriptManager with the specified logger and plugin name.
     *
     * @param logger     The logger instance used for logging.
     * @param pluginName The name of the plugin, used to determine the script's directory.
     */
    public ScriptManager(Logger logger, String pluginName) {
        this.logger = logger;
        this.scriptsDirectory = "plugins" + File.separator + pluginName + File.separator + "scripts";

        // Set YAML configuration options
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yaml = new Yaml(options);
    }

    /**
     * Loads all YAML script files in the scripts directory.
     */
    public void loadAllScripts() {
        File dir = new File(scriptsDirectory);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Failed to create scripts directory: " + scriptsDirectory);
        }

        try {
            Files.list(dir.toPath())
                    .filter(path -> path.toString().endsWith(".yml"))
                    .forEach(this::loadScriptFile);

            logger.debug("All script files have been loaded from directory: {}", scriptsDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load scripts: " + e.getMessage(), e);
        }
    }

    /**
     * Reloads all scripts by clearing the current cache and loading the files again.
     */
    public void reload() {
        // Clear the current scripts map
        scripts.clear();

        // Reload all scripts from the directory
        loadAllScripts();

        logger.info("All scripts have been successfully reloaded");
    }


    /**
     * Loads a single YAML script file and stores it in the scripts map.
     *
     * @param path The path to the YAML script file
     */
    private void loadScriptFile(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Map<String, Object> fileData = yaml.load(inputStream);
            if (fileData == null) fileData = new HashMap<>();

            ScriptConfig scriptConfig = new ScriptConfig(fileData);
            scripts.put(path.getFileName().toString(), scriptConfig);

            logger.debug("Script file loaded successfully: {}", path.getFileName().toString());

            // Trigger the onFileProcessed method after loading the file
            onFileProcessed(path.getFileName().toString(), scriptConfig);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load script file '" + path.getFileName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a ScriptConfig by file name.
     *
     * @param fileName The name of the script file
     * @return The ScriptConfig object or null if not found
     */
    public ScriptConfig getScriptConfig(String fileName) {
        logger.debug("Retrieved config");
        return scripts.get(fileName);
    }

    /**
     * Copies a default script file from resources if it does not exist.
     *
     * @param resourceName The name of the resource to copy
     * @param targetFileName The target name for the script file
     */
    public void copyDefaultScript(String resourceName, String targetFileName) {
        File scriptDir = new File(scriptsDirectory);
        if (!scriptDir.exists() && !scriptDir.mkdirs()) {
            throw new RuntimeException("Failed to create script directory: " + scriptsDirectory);
        }

        File scriptFile = new File(scriptDir, targetFileName);

        if (scriptFile.exists()) {
            logger.debug("Script file '{}' already exists, skipping copy", scriptFile.getAbsolutePath());
            return;
        }

        // Copy script file from resources
        try (InputStream in = getClass().getResourceAsStream("/" + resourceName);
             OutputStream out = Files.newOutputStream(scriptFile.toPath())) {
            if (in == null) {
                throw new RuntimeException("Resource '" + resourceName + "' not found in the plugin JAR");
            }

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            logger.info("Default script '{}' copied to: {}", resourceName, scriptFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy default script file " + resourceName + ": " + e.getMessage(), e);
        }
    }


    /**
     * ScriptConfig class represents the configuration of a script.
     */
    public static class ScriptConfig {
        private final String name;
        private final boolean enabled;
        private final boolean ignorePermissionCheck;
        private final boolean hidePermissionWarning;
        private final List<Command> commands;

        @SuppressWarnings("unchecked")
        public ScriptConfig(Map<String, Object> data) {
            this.name = (String) data.getOrDefault("name", "Unnamed Command");
            this.enabled = (boolean) data.getOrDefault("enabled", false);
            this.ignorePermissionCheck = (boolean) data.getOrDefault("ignore-permission-check", false);
            this.hidePermissionWarning = (boolean) data.getOrDefault("hide-permission-warning", false);

            this.commands = new ArrayList<>();
            Object commandsObject = data.get("commands");
            if (commandsObject instanceof List<?> commandsList) {
                for (Object commandData : commandsList) {
                    if (commandData instanceof Map) {
                        commands.add(new Command((Map<String, Object>) commandData));
                    }
                }
            }
        }

        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public boolean shouldIgnorePermissionCheck() { return ignorePermissionCheck; }
        public boolean shouldHidePermissionWarning() { return hidePermissionWarning; }
        public List<Command> getCommands() { return commands; }
    }

    /**
     * Command class represents a single command configuration.
     */
    public static class Command {
        private final String command;
        private final int delay;
        private final List<String> targetClientIds;
        private final String targetExecutor;
        private final boolean waitUntilPlayerIsOnline;
        private final boolean checkIfExecutorIsPlayer;
        private final boolean checkIfExecutorIsOnServer;

        /**
         * Creates a new Command object based on the given data.
         * @param data A map containing the command configuration values.
         */
        @SuppressWarnings("unchecked")
        public Command(Map<String, Object> data) {
            this.command = (String) data.get("command");
            this.delay = (int) data.getOrDefault("delay", 0);
            this.targetClientIds = (List<String>) data.getOrDefault("target-client-ids", new ArrayList<>());
            this.targetExecutor = (String) data.getOrDefault("target-executor", "console");
            this.waitUntilPlayerIsOnline = (boolean) data.getOrDefault("wait-until-player-is-online", false);
            this.checkIfExecutorIsPlayer = (boolean) data.getOrDefault("check-if-executor-is-player", true);
            this.checkIfExecutorIsOnServer = (boolean) data.getOrDefault("check-if-executor-is-on-server", true);
        }


        /**
         * @return The command string to be executed.
         */
        public String getCommand() { return command; }

        /**
         * @return The delay in seconds before the command is executed.
         */
        public int getDelay() { return delay; }

        /**
         * @return A list of target server IDs where the command should be executed.
         */
        public List<String> getTargetClientIds() { return targetClientIds; }

        /**
         * @return The executor that should run the command.
         */
        public String getTargetExecutor() { return targetExecutor; }

        /**
         * @return Whether the command should wait until the player is online before execution.
         */
        public boolean shouldWaitUntilPlayerIsOnline() { return waitUntilPlayerIsOnline; }

        /**
         * @return Whether the server should check if the executor is an instance of Player.
         */
        public boolean isCheckIfExecutorIsPlayer() { return checkIfExecutorIsPlayer; }

        /**
         * @return Whether the server should check if the executor is present on the target server.
         */
        public boolean isCheckIfExecutorIsOnServer() { return checkIfExecutorIsOnServer; }
    }

    /**
     * Called after each script file is loaded. This method allows subclasses to perform
     * specific actions with the loaded script data, such as processing or initializing.
     *
     * @param fileName    The name of the script file that was processed
     * @param scriptConfig The ScriptConfig object containing the configuration data
     */
    public abstract void onFileProcessed(String fileName, ScriptConfig scriptConfig);
}

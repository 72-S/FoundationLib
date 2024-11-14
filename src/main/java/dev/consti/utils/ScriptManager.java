package dev.consti.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import dev.consti.logging.Logger;

public abstract class ScriptManager {
    private final Map<String, ScriptConfig> scripts = new HashMap<>();
    private final Yaml yaml;
    private final Logger logger;
    private final String scriptsDirectory;

    public ScriptManager(Logger logger, String pluginName) {
        this.logger = logger;
        this.scriptsDirectory = "plugins" + File.separator + pluginName + File.separator + "scripts";

        // Set YAML configuration options
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yaml = new Yaml(options);

        loadAllScripts();
    }

    /**
     * Loads all YAML script files in the scripts directory.
     */
    private void loadAllScripts() {
        File dir = new File(scriptsDirectory);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.error("Failed to create scripts directory: {}", scriptsDirectory);
            return;
        }

        try {
            Files.list(dir.toPath())
                    .filter(path -> path.toString().endsWith(".yml"))
                    .forEach(this::loadScriptFile);
        } catch (IOException e) {
            logger.error("Failed to load scripts: {}", e.getMessage());
        }
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

            logger.info("Script file loaded: {}", path.getFileName().toString());

            // Trigger the onFileProcessed method after loading the file
            onFileProcessed(path.getFileName().toString(), scriptConfig);
        } catch (IOException e) {
            logger.error("Failed to load script file {}: {}", path.getFileName(), e.getMessage());
        }
    }

    /**
     * Retrieves a ScriptConfig by file name.
     *
     * @param fileName The name of the script file
     * @return The ScriptConfig object or null if not found
     */
    public ScriptConfig getScriptConfig(String fileName) {
        return scripts.get(fileName);
    }

    /**
     * Copies a default script file from resources if it does not exist.
     *
     * @param resourceName The name of the resource to copy
     */
    public void copyDefaultScript(String resourceName) {
        Path targetPath = Path.of(scriptsDirectory, resourceName);
        if (Files.exists(targetPath)) {
            return;
        }

        try (InputStream in = getClass().getResourceAsStream("/" + resourceName)) {
            if (in != null) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Default script copied to: {}", targetPath.toString());
            } else {
                logger.error("Resource {} not found.", resourceName);
            }
        } catch (IOException e) {
            logger.error("Failed to copy default script file {}: {}", resourceName, e.getMessage());
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
        private final List<String> targetServerIds;
        private final String targetExecutor;
        private final boolean waitUntilPlayerIsOnline;
        private final boolean checkIfExecutorIsPlayer;
        private final boolean checkIfExecutorIsOnServer;

        public Command(Map<String, Object> data) {
            this.command = (String) data.get("command");
            this.delay = (int) data.getOrDefault("delay", 0);
            this.targetServerIds = (List<String>) data.getOrDefault("target-server-ids", new ArrayList<>());
            this.targetExecutor = (String) data.getOrDefault("target-executor", "console");
            this.waitUntilPlayerIsOnline = (boolean) data.getOrDefault("wait-until-player-is-online", false);
            this.checkIfExecutorIsPlayer = (boolean) data.getOrDefault("check-if-executor-is-player", true);
            this.checkIfExecutorIsOnServer = (boolean) data.getOrDefault("check-if-executor-is-on-server", true);
        }

        public String getCommand() { return command; }
        public int getDelay() { return delay; }
        public List<String> getTargetServerIds() { return targetServerIds; }
        public String getTargetExecutor() { return targetExecutor; }
        public boolean shouldWaitUntilPlayerIsOnline() { return waitUntilPlayerIsOnline; }
        public boolean isCheckIfExecutorIsPlayer() { return checkIfExecutorIsPlayer; }
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

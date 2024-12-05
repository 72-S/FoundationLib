package dev.consti.foundationlib.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Utility class for checking plugin versions and performing version comparisons.
 * This class fetches the latest version of a project from Modrinth, checks if the current version is outdated,
 * and provides a download URL for the latest version.
 */
public class VersionChecker {

    /**
     * The project ID on Modrinth. This ID is used in API requests to fetch project version information.
     */
    private static String projectId;

    /**
     * The base URL for the Modrinth API to retrieve project versions. The project ID is added dynamically.
     */
    private static String MODRINTH_API_URL; 
    /**
     * A pattern to match numeric components of a version string.
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+");

    /**
     * Sets the project ID for the Modrinth API URL.
     *
     * @param id the unique project ID on Modrinth
     */
    public static void setProjectId(String id) {
        projectId = id;
        MODRINTH_API_URL = "https://api.modrinth.com/v2/project/" + projectId + "/version";
    }

    /**
     * Retrieves the latest version of the project from the Modrinth API.
     *
     * @return the latest version as a string, or {@code null} if the version could not be fetched
     */
    public static String getLatestVersion() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MODRINTH_API_URL))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            JSONArray versions = new JSONArray(responseBody);

            if (!versions.isEmpty()) {
                JSONObject latestVersion = versions.getJSONObject(0);
                return latestVersion.getString("version_number");
            }
        } catch (IOException | InterruptedException ignored) {
        }
        return null;
    }

    /**
     * Compares the latest version with the current version to determine if an update is available.
     *
     * @param latestVersion the latest version as a string
     * @param currentVersion the current version as a string
     * @return {@code true} if the latest version is newer than the current version, {@code false} otherwise
     */
    public static boolean isNewerVersion(String latestVersion, String currentVersion) {
        String[] latestParts = latestVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");

        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;

            if (latestPart > currentPart) {
                return true;
            } else if (latestPart < currentPart) {
                return false;
            }
        }
        return false;
    }

    /**
     * Parses a single part of a version string to an integer.
     *
     * @param versionPart the version part as a string
     * @return the integer representation of the version part, or 0 if no numeric part is found
     */
    private static int parseVersionPart(String versionPart) {
        var matcher = VERSION_PATTERN.matcher(versionPart);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return 0;
    }

    /**
     * Returns the URL where the latest version of the project can be downloaded.
     *
     * @return the URL string for the latest version download
     */
    public static String getDownloadUrl() {
        return "https://modrinth.com/plugin/" + projectId + "/versions";
    }

    /**
     * Checks if the provided Bukkit version matches the specified current version.
     *
     * @param bukkitVersion the version of the Bukkit server as a string
     * @param currentVersion the version to check against as a string
     * @return {@code true} if the versions match, {@code false} otherwise
     */
    public static boolean checkBukkitVersion(String bukkitVersion, String currentVersion) {
        String[] bukkitParts = bukkitVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");

        for (int i = 0; i < Math.min(bukkitParts.length, currentParts.length); i++) {
            if (!bukkitParts[i].equals(currentParts[i])) {
                return false;
            }
        }
        return bukkitParts.length == currentParts.length;
    }
}

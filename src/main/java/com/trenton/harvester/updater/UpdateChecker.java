package com.trenton.harvester.updater;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class UpdateChecker {

    private final JavaPlugin plugin;
    private final int resourceId;
    private String latestVersion;
    private boolean updateAvailable;

    public UpdateChecker(JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
        this.updateAvailable = false;
    }

    public void checkForUpdates(boolean autoUpdate) {
        try {
            URL url = new URL("https://api.spiget.org/v2/resources/" + resourceId + "/versions/latest");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Harvester-UpdateChecker");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            JSONObject json = new JSONObject(response.toString());
            if (!json.has("name")) {
                plugin.getLogger().warning("Spiget API response missing 'name' field: " + response);
                return;
            }
            latestVersion = json.getString("name");
            String currentVersion = plugin.getDescription().getVersion();
            if (!currentVersion.equals(latestVersion)) {
                updateAvailable = true;
                plugin.getLogger().info("Update available: Harvester v" + latestVersion + " (current: v" + currentVersion + ")");
                if (autoUpdate) {
                    downloadUpdate();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
        }
    }

    private void downloadUpdate() {
        try {
            URL url = new URL("https://api.spiget.org/v2/resources/" + resourceId + "/download");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Harvester-UpdateChecker");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            InputStream inputStream = connection.getInputStream();
            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }
            File outputFile = new File(updateFolder, "Harvester-" + latestVersion + ".jar");
            ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            fileOutputStream.close();
            inputStream.close();
            plugin.getLogger().info("Downloaded update to " + outputFile.getPath() + ". Restart server to apply.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to download update: " + e.getMessage());
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }
}
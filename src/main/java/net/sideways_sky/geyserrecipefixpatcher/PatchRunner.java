package net.sideways_sky.geyserrecipefixpatcher;

import net.sideways_sky.geyserrecipefixpatcher.net.ModrinthClient;
import net.sideways_sky.geyserrecipefixpatcher.transform.JarPatcher;
import net.sideways_sky.geyserrecipefixpatcher.util.PatchState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class PatchRunner {

    private final JavaPlugin plugin;
    private final ModrinthClient modrinth = new ModrinthClient();
    private final JarPatcher jarPatcher = new JarPatcher();
    private final PatchState state;

    public PatchRunner(JavaPlugin plugin) throws IOException {
        this.plugin = plugin;
        this.state = new PatchState(plugin.getDataFolder().toPath());
    }

    public enum Result {UP_TO_DATE, INSTALLED_FIRST_RUN, STAGED_RESTART_REQUIRED, ALLOWLIST_BLOCKED, FAILED}

    /** Runs the full check -> download -> patch -> install flow. Safe to call from onEnable(). */
    public Result run(Consumer<String> log) {
        FileConfiguration cfg = plugin.getConfig();
        String projectSlug = cfg.getString("modrinth-project", "geyser-recipe-fix");
        List<String> allowlist = cfg.getStringList("allowed-versions");
        boolean autoUpdate = cfg.getBoolean("auto-update", true);

        try {
            ModrinthClient.ReleaseInfo release = modrinth.fetchLatest(projectSlug);
            log.accept("Latest upstream release on Modrinth: " + release.versionNumber() + " (" + release.fileName() + ")");

            if (!allowlist.isEmpty() && !allowlist.contains(release.versionNumber())) {
                log.accept("Version " + release.versionNumber() + " is not in allowed-versions in config.yml - skipping. "
                        + "Add it there once you've verified it works.");
                return Result.ALLOWLIST_BLOCKED;
            }

            String installed = state.installedVersion();
            if (release.versionNumber().equals(installed)) {
                log.accept("Already up to date (v" + installed + "). Nothing to do.");
                return Result.UP_TO_DATE;
            }
            if (installed != null && !autoUpdate) {
                log.accept("A newer version (" + release.versionNumber() + ") is available but auto-update is off.");
                return Result.UP_TO_DATE;
            }

            Path work = plugin.getDataFolder().toPath().resolve("work");
            Files.createDirectories(work);
            Path downloaded = work.resolve(release.fileName());
            log.accept("Downloading official build from Modrinth...");
            modrinth.download(release.downloadUrl(), downloaded);

            Path pluginsDir = plugin.getDataFolder().toPath().getParent();
            Path targetJar = pluginsDir.resolve("GeyserRecipeFix.jar");
            boolean firstInstall = !Files.exists(targetJar);

            log.accept("Applying local patches...");
            jarPatcher.patch(downloaded, targetJar, getClass().getClassLoader());

            state.setInstalledVersion(release.versionNumber());
            Files.deleteIfExists(downloaded);

            if (firstInstall) {
                log.accept("Installed GeyserRecipeFix v" + release.versionNumber() + " to plugins/GeyserRecipeFix.jar. "
                        + "Restart the server to load it.");
                return Result.INSTALLED_FIRST_RUN;
            } else {
                log.accept("Updated GeyserRecipeFix to v" + release.versionNumber() + ". Restart the server to apply it.");
                return Result.STAGED_RESTART_REQUIRED;
            }

        } catch (JarPatcher.UnsupportedUpstreamVersionException e) {
            log.accept("Patch NOT applied: " + e.getMessage());
            return Result.FAILED;
        } catch (Exception e) {
            log.accept("Patch run failed: " + e);
            return Result.FAILED;
        }
    }

    public PatchState state() {
        return state;
    }
}

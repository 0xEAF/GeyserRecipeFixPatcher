package net.sideways_sky.geyserrecipefixpatcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PatchState {

    private final Path stateFile;
    private final Properties props = new Properties();

    public PatchState(Path dataFolder) throws IOException {
        this.stateFile = dataFolder.resolve("state.properties");
        if (Files.exists(stateFile)) {
            try (InputStream in = Files.newInputStream(stateFile)) {
                props.load(in);
            }
        }
    }

    public String installedVersion() {
        return props.getProperty("installed-version");
    }

    public void setInstalledVersion(String version) throws IOException {
        props.setProperty("installed-version", version);
        save();
    }

    public String stagedVersion() {
        return props.getProperty("staged-version");
    }

    public void setStagedVersion(String version) throws IOException {
        if (version == null) {
            props.remove("staged-version");
        } else {
            props.setProperty("staged-version", version);
        }
        save();
    }

    private void save() throws IOException {
        Files.createDirectories(stateFile.getParent());
        try (OutputStream out = Files.newOutputStream(stateFile)) {
            props.store(out, "GeyserRecipeFixPatcher state - do not edit by hand");
        }
    }
}

package io.github.nickm980.smallville.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Reads and writes the simulation to world-data/world.json.
 * <p>
 * Mirrors StoryStore's pattern. Until now the story and the location images
 * survived a restart while the agents they described did not, so a rebuild left
 * a lovingly written narrative about people who no longer existed.
 */
public class WorldStore {

    private static final Logger LOG = LoggerFactory.getLogger(WorldStore.class);
    private static final Path WORLD_FILE = Path.of("world-data", "world.json");

    private final ObjectMapper mapper;

    public WorldStore() {
	this.mapper = new ObjectMapper();
	this.mapper.registerModule(new JavaTimeModule());
	this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Optional<WorldSnapshot> load() {
	File file = WORLD_FILE.toFile();

	if (!file.exists()) {
	    return Optional.empty();
	}

	try {
	    return Optional.of(mapper.readValue(file, WorldSnapshot.class));
	} catch (IOException e) {
	    LOG.error("Could not read world-data/world.json, starting from an empty world", e);
	    return Optional.empty();
	}
    }

    /**
     * Writes to a temporary file and moves it into place, so a crash or a kill
     * partway through a write cannot leave a half-written world that fails to
     * load on the next start. Saves run on a timer, so this is not rare.
     */
    public void save(WorldSnapshot snapshot) {
	try {
	    Files.createDirectories(WORLD_FILE.getParent());

	    Path temp = WORLD_FILE.resolveSibling("world.json.tmp");
	    mapper.writeValue(temp.toFile(), snapshot);

	    try {
		Files.move(temp, WORLD_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	    } catch (IOException atomicUnsupported) {
		Files.move(temp, WORLD_FILE, StandardCopyOption.REPLACE_EXISTING);
	    }
	} catch (IOException e) {
	    LOG.error("Failed to write world-data/world.json", e);
	}
    }

    public void clear() {
	File file = WORLD_FILE.toFile();

	if (file.exists() && !file.delete()) {
	    LOG.warn("Could not delete world-data/world.json");
	}
    }
}

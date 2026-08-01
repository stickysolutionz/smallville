package io.github.nickm980.smallville.story;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// The only file I/O in this backend. Everything else lives in memory and is
// lost on restart; the generated story is worth persisting on its own,
// small, self-contained JSON file rather than pulling in a real persistence
// layer for one global blob.
public class StoryStore {

    private static final Logger LOG = LoggerFactory.getLogger(StoryStore.class);
    private static final Path STORY_FILE = Path.of("story-data", "story.json");

    private final ObjectMapper mapper;

    public StoryStore() {
	this.mapper = new ObjectMapper();
	this.mapper.registerModule(new JavaTimeModule());
	this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Optional<StorySnapshot> load() {
	File file = STORY_FILE.toFile();
	if (!file.exists()) {
	    return Optional.empty();
	}

	try {
	    return Optional.of(mapper.readValue(file, StorySnapshot.class));
	} catch (IOException e) {
	    LOG.warn("Failed to read story-data/story.json, treating as no story yet", e);
	    return Optional.empty();
	}
    }

    public void save(StorySnapshot snapshot) {
	try {
	    Files.createDirectories(STORY_FILE.getParent());
	    mapper.writeValue(STORY_FILE.toFile(), snapshot);
	} catch (IOException e) {
	    LOG.error("Failed to write story-data/story.json", e);
	}
    }

    public void clear() {
	File file = STORY_FILE.toFile();
	if (file.exists() && !file.delete()) {
	    LOG.warn("Could not delete story-data/story.json");
	}
    }
}

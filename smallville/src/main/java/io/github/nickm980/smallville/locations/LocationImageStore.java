package io.github.nickm980.smallville.locations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// The only image storage this backend has - mirrors StoryStore's pattern of
// "durable on disk, survives a restart even though nothing else does."
// index.json maps location name -> metadata; actual bytes live under an
// opaque UUID-based filename, so location names never need to be made
// filesystem-safe.
public class LocationImageStore {

    private static final Logger LOG = LoggerFactory.getLogger(LocationImageStore.class);
    private static final Path IMAGE_DIR = Path.of("location-images");
    private static final Path INDEX_FILE = IMAGE_DIR.resolve("index.json");

    private final ObjectMapper mapper;

    public LocationImageStore() {
	this.mapper = new ObjectMapper();
	this.mapper.registerModule(new JavaTimeModule());
	this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public LocationImageMeta save(String locationName, byte[] bytes, String contentType) {
	try {
	    Files.createDirectories(IMAGE_DIR);
	} catch (IOException e) {
	    throw new RuntimeException("Could not create location-images directory", e);
	}

	Map<String, LocationImageMeta> index = loadIndex();

	LocationImageMeta previous = index.get(locationName);
	if (previous != null) {
	    File oldFile = IMAGE_DIR.resolve(previous.getFilename()).toFile();
	    if (oldFile.exists() && !oldFile.delete()) {
		LOG.warn("Could not delete previous image file {} for {}", previous.getFilename(), locationName);
	    }
	}

	String filename = UUID.randomUUID() + extensionFor(contentType);
	try {
	    Files.write(IMAGE_DIR.resolve(filename), bytes);
	} catch (IOException e) {
	    throw new RuntimeException("Could not write image file for " + locationName, e);
	}

	LocationImageMeta meta = new LocationImageMeta(filename, contentType, bytes.length, LocalDateTime.now());
	index.put(locationName, meta);
	saveIndex(index);

	return meta;
    }

    public Optional<LocationImageMeta> find(String locationName) {
	return Optional.ofNullable(loadIndex().get(locationName));
    }

    public boolean hasImage(String locationName) {
	return loadIndex().containsKey(locationName);
    }

    public Optional<byte[]> readBytes(String locationName) {
	return find(locationName).flatMap(meta -> {
	    try {
		return Optional.of(Files.readAllBytes(IMAGE_DIR.resolve(meta.getFilename())));
	    } catch (IOException e) {
		LOG.warn("Could not read image file {} for {}", meta.getFilename(), locationName, e);
		return Optional.empty();
	    }
	});
    }

    private String extensionFor(String contentType) {
	switch (contentType) {
	case "image/png":
	    return ".png";
	case "image/webp":
	    return ".webp";
	default:
	    return ".jpg";
	}
    }

    private Map<String, LocationImageMeta> loadIndex() {
	File file = INDEX_FILE.toFile();
	if (!file.exists()) {
	    return new HashMap<>();
	}
	try {
	    return mapper.readValue(file, new TypeReference<Map<String, LocationImageMeta>>() {
	    });
	} catch (IOException e) {
	    LOG.warn("Failed to read location-images/index.json, starting fresh", e);
	    return new HashMap<>();
	}
    }

    private void saveIndex(Map<String, LocationImageMeta> index) {
	try {
	    mapper.writeValue(INDEX_FILE.toFile(), index);
	} catch (IOException e) {
	    LOG.error("Failed to write location-images/index.json", e);
	}
    }
}

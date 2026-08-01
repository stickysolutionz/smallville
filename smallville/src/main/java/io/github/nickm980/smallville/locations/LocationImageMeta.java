package io.github.nickm980.smallville.locations;

import java.time.LocalDateTime;

// One entry in location-images/index.json, keyed by the location's raw
// name. filename is an opaque generated name (never derived from the
// location's own text), so there's no need for any filename-sanitization
// scheme even though location names can contain spaces and colons.
public class LocationImageMeta {

    private String filename;
    private String contentType;
    private long sizeBytes;
    private LocalDateTime uploadedAt;

    public LocationImageMeta() {
    }

    public LocationImageMeta(String filename, String contentType, long sizeBytes, LocalDateTime uploadedAt) {
	this.filename = filename;
	this.contentType = contentType;
	this.sizeBytes = sizeBytes;
	this.uploadedAt = uploadedAt;
    }

    public String getFilename() {
	return filename;
    }

    public void setFilename(String filename) {
	this.filename = filename;
    }

    public String getContentType() {
	return contentType;
    }

    public void setContentType(String contentType) {
	this.contentType = contentType;
    }

    public long getSizeBytes() {
	return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
	this.sizeBytes = sizeBytes;
    }

    public LocalDateTime getUploadedAt() {
	return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
	this.uploadedAt = uploadedAt;
    }
}

package io.github.nickm980.smallville.api.v1.dto;

public class LocationStateResponse {

    private String name;
    private String state;
    private boolean hasImage;

    public String getName() {
	return name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getState() {
	return state;
    }

    public void setState(String state) {
	this.state = state;
    }

    public boolean isHasImage() {
	return hasImage;
    }

    public void setHasImage(boolean hasImage) {
	this.hasImage = hasImage;
    }
}

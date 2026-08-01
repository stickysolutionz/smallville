package io.github.nickm980.smallville.config;

public class GeneralConfig {

    private String apiPath;
    private String timeFormat;
    private String fullTimeFormat;
    private String yesterdayFormat;
    private String model;
    private int reflectionCutoff;
    private boolean simulationFile;
    private int maxRetries;

    // Memory retrieval. Defaults are applied here rather than relying on the
    // yaml so an older config.yaml sitting next to the jar still starts.
    private double recencyHalfLifeHours = 8;
    private int retrievalCount = 3;
    private double recencyWeight = 1;
    private double importanceWeight = 1;
    private double relevanceWeight = 1;

    public double getRecencyHalfLifeHours() {
	return recencyHalfLifeHours;
    }

    public void setRecencyHalfLifeHours(double recencyHalfLifeHours) {
	this.recencyHalfLifeHours = recencyHalfLifeHours;
    }

    public int getRetrievalCount() {
	return retrievalCount;
    }

    public void setRetrievalCount(int retrievalCount) {
	this.retrievalCount = retrievalCount;
    }

    public double getRecencyWeight() {
	return recencyWeight;
    }

    public void setRecencyWeight(double recencyWeight) {
	this.recencyWeight = recencyWeight;
    }

    public double getImportanceWeight() {
	return importanceWeight;
    }

    public void setImportanceWeight(double importanceWeight) {
	this.importanceWeight = importanceWeight;
    }

    public double getRelevanceWeight() {
	return relevanceWeight;
    }

    public void setRelevanceWeight(double relevanceWeight) {
	this.relevanceWeight = relevanceWeight;
    }

    public boolean isSimulationFile() {
	return simulationFile;
    }

    public void setSimulationFile(boolean useSimulationFile) {
	this.simulationFile = useSimulationFile;
    }

    public int getReflectionCutoff() {
	return reflectionCutoff;
    }

    public void setReflectionCutoff(int reflectionCutoff) {
	this.reflectionCutoff = reflectionCutoff;
    }

    public String getModel() {
	return model;
    }

    public void setModel(String model) {
	this.model = model;
    }

    public String getYesterdayFormat() {
	return yesterdayFormat;
    }

    public void setYesterdayFormat(String yesterdayFormat) {
	this.yesterdayFormat = yesterdayFormat;
    }

    public String getFullTimeFormat() {
	return fullTimeFormat;
    }

    public void setFullTimeFormat(String fullTimeFormat) {
	this.fullTimeFormat = fullTimeFormat;
    }

    public String getTimeFormat() {
	return timeFormat;
    }

    public void setTimeFormat(String timeFormat) {
	this.timeFormat = timeFormat;
    }

    public String getApiPath() {
	return apiPath;
    }

    public void setApiPath(String path) {
	this.apiPath = path;
    }

    public int getMaxRetries() {
	return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
	this.maxRetries = maxRetries;
    }

}

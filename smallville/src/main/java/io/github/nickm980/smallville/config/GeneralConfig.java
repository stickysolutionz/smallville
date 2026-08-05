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

    // Model routing, thinking mode and pricing. Defaults are applied here
    // rather than relying on the yaml so an older config.yaml sitting next to
    // the jar still starts.
    private String cheapModel;
    private String thinking;
    private double inputPricePerMillion = 0.435;
    private double cachedInputPricePerMillion = 0.003625;
    private double outputPricePerMillion = 0.87;

    public String getCheapModel() {
	return cheapModel;
    }

    public void setCheapModel(String cheapModel) {
	this.cheapModel = cheapModel;
    }

    public String getThinking() {
	return thinking;
    }

    public void setThinking(String thinking) {
	this.thinking = thinking;
    }

    public double getInputPricePerMillion() {
	return inputPricePerMillion;
    }

    public void setInputPricePerMillion(double inputPricePerMillion) {
	this.inputPricePerMillion = inputPricePerMillion;
    }

    public double getCachedInputPricePerMillion() {
	return cachedInputPricePerMillion;
    }

    public void setCachedInputPricePerMillion(double cachedInputPricePerMillion) {
	this.cachedInputPricePerMillion = cachedInputPricePerMillion;
    }

    public double getOutputPricePerMillion() {
	return outputPricePerMillion;
    }

    public void setOutputPricePerMillion(double outputPricePerMillion) {
	this.outputPricePerMillion = outputPricePerMillion;
    }

    // Memory retrieval. Defaults are applied here rather than relying on the
    // yaml so an older config.yaml sitting next to the jar still starts.
    /**
     * How often something from outside the town lands on somebody, per
     * simulated day, across the whole town. Rare on purpose - a town where
     * something happens to somebody once a day is a town; one where everybody
     * gets news every morning is a soap opera. Set to 0 to switch it off.
     */
    private double eventsPerSimulatedDay = 1.0;

    public double getEventsPerSimulatedDay() {
	return eventsPerSimulatedDay;
    }

    public void setEventsPerSimulatedDay(double eventsPerSimulatedDay) {
	this.eventsPerSimulatedDay = eventsPerSimulatedDay;
    }

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

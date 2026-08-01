package io.github.nickm980.smallville.api.v1;

import static io.github.nickm980.smallville.api.SmallvilleServer.exists;

import java.io.StringWriter;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.github.nickm980.smallville.analytics.Analytics;
import io.github.nickm980.smallville.api.v1.dto.*;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.javalin.community.routing.annotations.Delete;
import io.javalin.community.routing.annotations.Endpoints;
import io.javalin.community.routing.annotations.Get;
import io.javalin.community.routing.annotations.Param;
import io.javalin.community.routing.annotations.Post;
import io.javalin.http.Context;

@Endpoints("/")
public final class SimulationController {

    private MustacheFactory mf;
    private Analytics analytics;
    private SimulationService service;
    private SimulationRunner runner;
    private Gson gson = new Gson();

    public SimulationController(Analytics analytics, SimulationService service, MustacheFactory mf) {
	this.mf = mf;
	this.analytics = analytics;
	this.service = service;
	this.runner = new SimulationRunner(service);
    }

    
    @Get("/ping")
    public void ping(Context ctx) {
	ctx.status(200).json(Map.of("success", true, "ping", "pong"));
    }
    
    @Post("/memories/stream")
    public void createMemoryStream(Context ctx) {
	UUID uuid = service.createMemoryStream();
	ctx.json(Map.of("success", true, "uuid", uuid));
    }

    @Post("/memories/stream/{uuid}")
    public void saveMemory(Context ctx, @Param String uuidStr) {
	UUID uuid = UUID.fromString(uuidStr);

	Map<String, String> dataMap = gson.fromJson(ctx.body(), new TypeToken<Map<String, String>>() {
	}.getType());

	String query = (String) dataMap.get("query");

	List<String> result = service.getMemories(uuid, query);
	ctx.status(200).json(Map.of("success", true, "memories", result));
    }

    @Get("/memories/{name}")
    public void getMemoryByName(Context ctx) {
	Map<String, Object> model = new HashMap<>();
	model.put("memories", service.getMemoriesOfAgent(ctx.pathParam("name")));

	Mustache mustache = mf.compile("agent.mustache");
	String output = mustache.execute(new StringWriter(), model).toString();
	ctx.html(output);
    }

    @Get("/conversations")
    public void getAllConversations(Context ctx) {
	ctx.json(Map.of("conversations", service.getAllConversations()));
    }

    @Get("/info")
    public void getInfo(Context ctx) {
	String time = SimulationTime.now().format(DateTimeFormatter.ofPattern("h:mm a"));

	ctx
	    .json(Map
		.of("time", time, "step", SimulationTime.getStepDurationInMinutes(), "prompts",
			analytics.getPromptHistory(), "locationVisits", analytics.getVisits()));
    }

    @Get("/agents")
    public void getAgents(Context ctx) {
	ctx.json(Map.of("agents", service.getAgents()));
    }

    @Get("/agents/{name}")
    public void getAgentsByName(Context ctx) {
	AgentStateResponse res = service.getAgentState(ctx.pathParam("name"));
	ctx.json(res);
    }

    @Post("/agents/{name}/ask")
    public void askAgentQuestion(Context ctx) {
	AskQuestionRequest request = ctx
	    .bodyValidator(AskQuestionRequest.class)
	    .check((req) -> exists(req.getQuestion()), "{question} cannot be blank")
	    .get();

	String res = service.askQuestion(ctx.pathParam("name"), request.getQuestion());

	ctx.json(Map.of("answer", res));
    }

    @Post("/agents/generate")
    public void generateCharacter(Context ctx) {
	GeneratedCharacterResponse result = service.generateCharacter();
	ctx.json(result);
    }

    @Get("/story")
    public void getStory(Context ctx) {
	ctx.json(service.getStory());
    }

    @Post("/story/generate")
    public void generateStory(Context ctx) {
	ctx.json(service.generateStory());
    }

    @Post("/agents")
    public void createAgent(Context ctx) {
	CreateAgentRequest request = ctx
	    .bodyValidator(CreateAgentRequest.class)
	    .check((req) -> exists(req.getName()), "{name} cannot be missing")
	    .check((req) -> exists(req.getActivity()), "{activity} cannot be missing")
	    .check((req) -> exists(req.getLocation()), "{location} cannot be missing")
	    .check((req) -> req.getMemories() != null && !req.getMemories().isEmpty(), "{memories} cannot be missing")
	    .get();

	service.createAgent(request);
	ctx.json(Map.of("success", true));
    }

    @Delete("/agents/{name}")
    public void deleteAgent(Context ctx) {
	service.deleteAgent(ctx.pathParam("name"));
	ctx.json(Map.of("success", true));
    }

    @Get("/agents/{name}/characteristics")
    public void getCharacteristics(Context ctx) {
	ctx.json(Map.of("characteristics", service.getCharacteristics(ctx.pathParam("name"))));
    }

    @Get("/agents/{name}/diary")
    public void getDiary(Context ctx) {
	ctx.json(Map.of("diary", service.getDiary(ctx.pathParam("name"))));
    }

    @Post("/agents/{name}/characteristics")
    public void addCharacteristic(Context ctx) throws JsonMappingException, JsonProcessingException {
	String name = ctx.pathParam("name");
	ObjectMapper objectMapper = new ObjectMapper();
	JsonNode rootNode = objectMapper.readTree(ctx.body());
	String description = rootNode.get("description").asText();

	service.addCharacteristic(name, description);
	ctx.json(Map.of("success", true, "characteristics", service.getCharacteristics(name)));
    }

    @Delete("/agents/{name}/characteristics/{index}")
    public void removeCharacteristic(Context ctx) {
	String name = ctx.pathParam("name");
	int index = Integer.parseInt(ctx.pathParam("index"));

	service.removeCharacteristic(name, index);
	ctx.json(Map.of("success", true, "characteristics", service.getCharacteristics(name)));
    }

    @Post("/locations")
    public void createLocation(Context ctx) {
	CreateLocationRequest request = ctx
	    .bodyValidator(CreateLocationRequest.class)
	    .check((req) -> exists(req.getName()), "{name} cannot be missing")
	    .get();

	service.createLocation(request);
	ctx.json(Map.of("success", true));
    }

    @Delete("/locations/{name}")
    public void deleteLocation(Context ctx) {
	service.deleteLocation(ctx.pathParam("name"));
	ctx.json(Map.of("success", true));
    }

    @Post("/locations/{name}")
    public void changeLocationState(Context ctx) throws JsonMappingException, JsonProcessingException {
	String location = ctx.pathParam("name");
	ObjectMapper objectMapper = new ObjectMapper();

	JsonNode rootNode = objectMapper.readTree(ctx.body());
	String state = rootNode.get("state").asText();

	service.setState(location, state);
	ctx.json(Map.of("success", true));
    }

    private static final java.util.Set<String> ALLOWED_IMAGE_TYPES = java.util.Set
	.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_IMAGE_BYTES = 5_000_000L;

    @Post("/locations/{name}/image")
    public void uploadLocationImage(Context ctx) throws java.io.IOException {
	String name = ctx.pathParam("name");
	io.javalin.http.UploadedFile file = ctx.uploadedFile("image");

	if (file == null) {
	    ctx.status(400).json(Map.of("success", false, "message", "No image file provided"));
	    return;
	}

	if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
	    ctx.status(415).json(Map.of("success", false, "message", "Only PNG, JPEG, or WebP images are allowed"));
	    return;
	}

	byte[] bytes = file.content().readAllBytes();

	if (bytes.length > MAX_IMAGE_BYTES) {
	    ctx.status(413).json(Map.of("success", false, "message", "Image must be under 5MB"));
	    return;
	}

	service.saveLocationImage(name, bytes, file.contentType());
	ctx.json(Map.of("success", true, "imageUrl", "/locations/" + name + "/image"));
    }

    @Get("/locations/{name}/image")
    public void getLocationImage(Context ctx) {
	String name = ctx.pathParam("name");
	var meta = service.findLocationImage(name);
	var bytes = service.readLocationImageBytes(name);

	if (meta.isEmpty() || bytes.isEmpty()) {
	    ctx.status(404);
	    return;
	}

	ctx.contentType(meta.get().getContentType());
	ctx.result(bytes.get());
    }

    @Get("/locations")
    public void getLocations(Context ctx) {
	List<LocationStateResponse> request = service.getAllLocations();

	ctx.json(Map.of("locations", request));
    }

    @Post("/memories")
    public void saveAgentMemory(Context ctx) {
	CreateMemoryRequest request = ctx.bodyAsClass(CreateMemoryRequest.class);
	service.createMemory(request);

	ctx.json(Map.of("success", true));
    }

    @Post("/state")
    public void updateState(Context ctx) {
	service.updateState();
	List<AgentStateResponse> agents = service.getAgents();
	List<LocationStateResponse> locations = service.getAllLocations();
	List<ConversationResponse> conversations = service.getConversations();

	ctx.json(Map.of("agents", agents, "location_states", locations, "conversations", conversations));
    }

    @Get("/state")
    public void getState(Context ctx) {
	List<AgentStateResponse> agents = service.getAgents();
	List<LocationStateResponse> locations = service.getAllLocations();
	List<ConversationResponse> conversations = service.getConversations();

	ctx.json(Map.of("agents", agents, "location_states", locations, "conversations", conversations));
    }

    @Post("/timestep")
    public void setTimestep(Context ctx) {
	SetTimestepRequest request = ctx.bodyAsClass(SetTimestepRequest.class);
	int minutes = Integer.valueOf(request.getNumOfMinutes());
	SimulationTime.setStep(Duration.ofMinutes(minutes));
	ctx.json(Map.of("success", true, "message", "Timestep updated to " + minutes + " per update"));
    }

    @Post("/simulation/start")
    public void startSimulation(Context ctx) throws JsonMappingException, JsonProcessingException {
	double intervalSeconds = 15;

	if (exists(ctx.body())) {
	    ObjectMapper objectMapper = new ObjectMapper();
	    JsonNode rootNode = objectMapper.readTree(ctx.body());

	    if (rootNode.has("intervalSeconds")) {
		intervalSeconds = rootNode.get("intervalSeconds").asDouble();
	    }
	}

	try {
	    runner.start(intervalSeconds);
	} catch (IllegalArgumentException e) {
	    ctx.status(400).json(Map.of("success", false, "message", e.getMessage()));
	    return;
	}

	ctx
	    .json(Map
		.of("success", true, "running", runner.isRunning(), "intervalSeconds", runner.getIntervalSeconds()));
    }

    @Post("/simulation/stop")
    public void stopSimulation(Context ctx) {
	runner.stop();
	ctx.json(Map.of("success", true, "running", runner.isRunning()));
    }

    @Get("/usage")
    public void getUsage(Context ctx) {
	ctx.json(io.github.nickm980.smallville.llm.UsageTracker.snapshot());
    }

    @Post("/usage/reset")
    public void resetUsage(Context ctx) {
	io.github.nickm980.smallville.llm.UsageTracker.reset();
	ctx.json(Map.of("success", true));
    }

    @Post("/simulation/reset")
    public void resetSimulation(Context ctx) {
	service.resetSimulationData();
	ctx.json(Map.of("success", true));
    }

    @Get("/simulation/status")
    public void getSimulationStatus(Context ctx) {
	String time = SimulationTime.now().format(DateTimeFormatter.ofPattern("h:mm a"));
	String date = SimulationTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"));

	Map<String, Object> status = new HashMap<>();
	status.put("running", runner.isRunning());
	status.put("intervalSeconds", runner.getIntervalSeconds());
	status.put("tickCount", runner.getTickCount());
	status.put("lastError", runner.getLastError());
	status.put("time", time);
	status.put("date", date);
	status.put("agentCount", service.getAgents().size());

	ctx.json(status);
    }
}

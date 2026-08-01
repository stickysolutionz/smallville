package io.github.nickm980.smallville.api;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;

import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.analytics.Analytics;
import io.github.nickm980.smallville.api.v1.SimulationController;
import io.github.nickm980.smallville.api.v1.SimulationService;
import io.github.nickm980.smallville.llm.LLM;
import io.javalin.Javalin;
import io.javalin.community.routing.annotations.AnnotatedRoutingPlugin;

public class SmallvilleServer {

    private static final Logger LOG = LoggerFactory.getLogger(SmallvilleServer.class);

    private final SimulationService service;
    private final ScheduledExecutorService autosave = Executors
	.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "smallville-autosave"));
    private MustacheFactory mf;
    private Analytics analytics;
    private Javalin server;

    public SmallvilleServer(Analytics analytics, LLM llm, World sim) {
	this.service = new SimulationService(llm, sim);
	this.mf = new DefaultMustacheFactory();
	this.analytics = analytics;
	this.server = Javalin.create(config -> {
	    config.showJavalinBanner = false;
	    // Above the 5MB app-level image size cap, to leave headroom for
	    // multipart boundary/field overhead - Javalin's own default is
	    // well under what a real photo upload needs.
	    config.http.maxRequestSize = 6_000_000L;
	    config.plugins.enableCors(cors -> {
		cors.add(it -> {
		    it.anyHost();
		});
	    });
	    AnnotatedRoutingPlugin routes = new AnnotatedRoutingPlugin();
	    routes.registerEndpoints(new SimulationController(analytics, service, mf));

	    config.plugins.register(routes);
	});

    }

    public SmallvilleServer start() {
	return start(8080);
    }

    /**
     * How often the world is written to disk while running. Frequent enough
     * that a crash costs a few minutes rather than a session, cheap enough
     * that it never competes with a tick for the lock in any meaningful way.
     */
    private static final long AUTOSAVE_SECONDS = 120;

    public SmallvilleServer start(int port) {
	service.loadWorld();

	autosave.scheduleWithFixedDelay(service::saveWorld, AUTOSAVE_SECONDS, AUTOSAVE_SECONDS, TimeUnit.SECONDS);

	// Also on the way out, so a clean stop keeps everything since the last
	// autosave rather than discarding it.
	Runtime.getRuntime().addShutdownHook(new Thread(() -> {
	    LOG.info("Shutting down, saving the world");
	    autosave.shutdownNow();
	    service.saveWorld();
	}, "smallville-shutdown"));

	server.start(port);
	return this;
    }

    public static boolean exists(String s) {
	return s != null && !s.isBlank();
    }

    public Javalin server() {
	return server;
    }
}

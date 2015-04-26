package io.galeb.services.api;

import io.galeb.core.services.AbstractService;
import io.galeb.services.api.jaxrs.ApiApplication;
import io.galeb.undertow.jaxrs.Deployer;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

public class Api extends AbstractService {

    private static final String PROP_MANAGER_PREFIX    = "io.galeb.api.";

    private static final String PROP_MANAGER_PORT      = PROP_MANAGER_PREFIX+"port";

    private static final String PROP_MANAGER_IOTHREADS = PROP_MANAGER_PREFIX+"iothread";

    static {
        if (System.getProperty(PROP_MANAGER_PORT)==null) {
            System.setProperty(PROP_MANAGER_PORT, "9090");
        }
        if (System.getProperty(PROP_MANAGER_IOTHREADS)==null) {
            System.setProperty(PROP_MANAGER_IOTHREADS, String.valueOf(Runtime.getRuntime().availableProcessors()));
        }
    }

    @PostConstruct
    protected void init() {

        super.prelaunch();

        int port = Integer.parseInt(System.getProperty(PROP_MANAGER_PORT));
        String iothreads = System.getProperty(PROP_MANAGER_IOTHREADS);

        final Map<String, String> options = new HashMap<>();
        options.put("IoThreads", iothreads);

        new Deployer().setHost("0.0.0.0")
                      .setPort(port)
                      .deploy(new ApiApplication().setManager(this))
                      .setOptions(options)
                      .start();

        logger.debug(String.format("[0.0.0.0:%d] ready", port));
    }

    public Api() {
        super();
    }

}

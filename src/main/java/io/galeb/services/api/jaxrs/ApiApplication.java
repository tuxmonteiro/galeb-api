package io.galeb.services.api.jaxrs;

import io.galeb.core.eventbus.IEventBus;
import io.galeb.core.model.Farm;
import io.galeb.services.api.Api;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/")
public class ApiApplication extends Application {

    private Farm farm;

    private IEventBus eventBus;

    public Application setManager(final Api api) {
        farm = api.getFarm();
        eventBus = api.getEventBus();
        return this;
    }

    public Farm getFarm() {
        return farm;
    }

    @Override
    public Set<Class<?>> getClasses() {
        final Set<Class<?>> classes = new HashSet<>();
        classes.add(ApiResources.class);
        return classes;
    }

    public IEventBus getEventBus() {
        return eventBus;
    }
}
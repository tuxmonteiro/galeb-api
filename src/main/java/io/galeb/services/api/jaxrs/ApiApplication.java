package io.galeb.services.api.jaxrs;

import io.galeb.services.api.Api;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import io.galeb.core.eventbus.IEventBus;
import io.galeb.core.model.Farm;
import io.galeb.services.api.jaxrs.ApiResources;

@ApplicationPath("/")
public class ApiApplication extends Application {

    private Farm farm;

    private IEventBus eventBus;

    public Application setManager(final Api api) {
        this.farm = api.getFarm();
        this.eventBus = api.getEventBus();
        return this;
    }

    public Farm getFarm() {
        return farm;
    }

    public IEventBus getEventBus() {
        return eventBus;
    }

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(ApiResources.class);
        return classes;
    }
}
package com.openvraas.services.manager.jaxrs;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import com.openvraas.core.model.Farm;
import com.openvraas.hazelcast.IEventBus;
import com.openvraas.services.manager.Manager;

@ApplicationPath("/")
public class ManagerApplication extends Application {

    private Farm farm;

    private IEventBus eventBus;

    public Application setManager(final Manager manager) {
        this.farm = manager.getFarm();
        this.eventBus = manager.getEventBus();
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
        classes.add(ManagerResources.class);
        return classes;
    }
}
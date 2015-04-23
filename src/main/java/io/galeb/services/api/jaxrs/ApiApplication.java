package io.galeb.services.api.jaxrs;

import io.galeb.services.api.Api;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import io.galeb.core.model.Farm;
import io.galeb.services.api.jaxrs.ApiResources;

@ApplicationPath("/")
public class ApiApplication extends Application {

    private Farm farm;

    public Application setManager(final Api api) {
        this.farm = api.getFarm();
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
}
package io.galeb.services.api.jaxrs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import io.galeb.core.controller.EntityController;
import io.galeb.core.controller.EntityController.Action;
import io.galeb.core.json.JsonObject;
import io.galeb.core.logging.Logger;
import io.galeb.core.model.Entity;
import io.galeb.core.model.Farm;
import io.galeb.hazelcast.IEventBus;

@Path("/")
public class ApiResources {

    @Inject
    protected Logger logger;

    @Context UriInfo uriInfo;

    @Context Request request;

    @Context Application application;

    @DefaultValue("") @PathParam("ENTITY_TYPE") String entityType;

    @DefaultValue("") @PathParam("ENTITY_ID") String entityId;

    private boolean entityExist = false;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getNull() {
       return Response.status(Status.FORBIDDEN).build(); //$NON-NLS-1$
    }

    @GET
    @Path("{ENTITY_TYPE}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("ENTITY_TYPE") String entityType) {

        final Farm farm = ((ApiApplication) application).getFarm();

        if (Farm.class.getSimpleName().toLowerCase().equals(entityType)) {
            return Response.ok(JsonObject.toJson(farm)).build();
        }

        EntityController entityController = farm.getEntityMap().get(entityType);

        if (entityController!=null) {
            return Response.ok(entityController.get(null)).build();
        }
       return Response.status(Status.NOT_FOUND).build(); //$NON-NLS-1$
    }

    @GET
    @Path("{ENTITY_TYPE}/{ENTITY_ID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOne() {

        String result = getEntity(entityType, entityId);
        entityExist = isEntityExist(entityType, entityId);

        if (entityExist) {
            return Response.ok(result).build();
        }

       return Response.status(Status.NOT_FOUND).build();
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response postNull() {
       return Response.status(Status.FORBIDDEN).build();
    }

    @POST
    @Path("{ENTITY_TYPE}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    @SuppressWarnings("finally")
    public Response post(InputStream is) {
        String entityStr = "";
        String entityIdFromEntity = "";

        try {
            entityStr = convertStreamToString(is);

            Entity entity = (Entity) JsonObject.fromJson(entityStr, Entity.class);
            entityIdFromEntity = entity.getId();

            final IEventBus eventBus = ((ApiApplication) application).getEventBus();

            eventBus.publishEntity(entity, entityType, Action.ADD);

        } catch (IOException e) {
            logger.error(e);
        } finally {
            entityExist = isEntityExist(entityType, entityIdFromEntity);
            return postAndGetResponse(!entityExist);
        }
    }

    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public Response putNull() {
       return Response.status(Status.FORBIDDEN).build();
    }

    @PUT
    @Path("{ENTITY_TYPE}/{ENTITY_ID}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    @SuppressWarnings("finally")
    public Response put(InputStream is) {
        String entityStr = "";
        String entityIdFromEntity = "";
        try {
            entityStr = convertStreamToString(is);

            Entity entity = (Entity) JsonObject.fromJson(entityStr, Entity.class);
            entityIdFromEntity = entity.getId();

            final IEventBus eventBus = ((ApiApplication) application).getEventBus();

            eventBus.publishEntity(entity, entityType, Action.CHANGE);

        } catch (IOException e) {
            logger.error(e);
        } finally {
            entityExist = isEntityExist(entityType, entityIdFromEntity);
            return putAndGetResponse(entityExist);
        }
    }

    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteNull() {
       return Response.status(Status.FORBIDDEN).build();
    }

    @DELETE
    @Path("{ENTITY_TYPE}/{ENTITY_ID}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    @SuppressWarnings("finally")
    public Response delete(InputStream is) {

        String entityStr = "";
        String entityIdFromEntity = "";
        try {
            entityStr = convertStreamToString(is);
            Entity entity = (Entity) JsonObject.fromJson(entityStr, Entity.class);
            entityIdFromEntity  = entity.getId();

            final IEventBus eventBus = ((ApiApplication) application).getEventBus();

            eventBus.publishEntity(entity, entityType, Action.DEL);

        } catch (IOException e) {
            logger.error(e);
        } finally {
            entityExist = isEntityExist(entityType, entityIdFromEntity);
            return deleteAndGetResponse(entityExist);
        }

    }

    private String getEntity(String entityType, String id) {

        final Farm farm = ((ApiApplication) application).getFarm();
        String result = "";

        EntityController entityController = farm.getEntityMap().get(entityType);

        if (entityController!=null) {
            result = entityController.get(id);
        }
        return result;
    }

    private boolean isEntityExist(String entityType, String id) {
        String result = getEntity(entityType, id);
        return result != null && !JsonObject.NULL.equals(result);
    }

    private Response deleteAndGetResponse(boolean entityExist) {
        return postAndGetResponse(entityExist);
    }

    private Response putAndGetResponse(boolean entityExist) {
        return postAndGetResponse(entityExist);
    }

    private Response postAndGetResponse(boolean entityExist) {
        Response res = null;
        if (entityExist) {
            res = Response.accepted().build();
        } else {
            res = Response.noContent().build();
        }
        return res;
    }

    private String convertStreamToString(InputStream is) throws IOException {

        if (is != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8")); //$NON-NLS-1$
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }
            return writer.toString();
        }
        return ""; //$NON-NLS-1$
    }

}
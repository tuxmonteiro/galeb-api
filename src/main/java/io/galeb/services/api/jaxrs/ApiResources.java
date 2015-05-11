/*
 * Copyright (c) 2014-2015 Globo.com - ATeam
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.galeb.services.api.jaxrs;

import io.galeb.core.controller.EntityController;
import io.galeb.core.controller.EntityController.Action;
import io.galeb.core.json.JsonObject;
import io.galeb.core.logging.Logger;
import io.galeb.core.model.BackendPool;
import io.galeb.core.model.Entity;
import io.galeb.core.model.Farm;
import io.galeb.core.model.VirtualHost;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

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
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

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
       return Response.status(Status.FORBIDDEN).build();
    }

    @GET
    @Path("{ENTITY_TYPE}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("ENTITY_TYPE") String entityType) {

        final Farm farm = ((ApiApplication) application).getFarm();

        if (Farm.class.getSimpleName().equalsIgnoreCase(entityType)) {
            return Response.ok(JsonObject.toJsonString(farm)).build();
        }

        final EntityController entityController = farm.getEntityMap().get(entityType);

        if (entityController!=null) {
            return Response.ok(entityController.get(null)).build();
        }
       return Response.status(Status.NOT_FOUND).build();
    }

    @GET
    @Path("{ENTITY_TYPE}/{ENTITY_ID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOne() {

        final String result = getEntity(entityType, entityId);
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
    public Response post(InputStream is) {
        String entityStr = "";
        String entityIdFromEntity = "";
        try {
            entityStr = convertStreamToString(is);

            final Entity entity = (Entity) JsonObject.fromJson(entityStr, Entity.class);
            entityIdFromEntity = entity.getId();
            entityExist = isEntityExist(entityType, entityIdFromEntity);

            ((ApiApplication) application).getEventBus().publishEntity(entity, entityType, Action.ADD);

        } catch (final IOException e) {
            logger.error(e);
            return postAndGetResponse(false);
        }
        return postAndGetResponse(!entityExist);
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
    public Response put(InputStream is) {
        String entityStr = "";
        String entityIdFromEntity = "";
        try {
            entityStr = convertStreamToString(is);

            final Entity entity = (Entity) JsonObject.fromJson(entityStr, Entity.class);
            entityIdFromEntity = entity.getId();
            entityExist = isEntityExist(entityType, entityIdFromEntity);

            ((ApiApplication) application).getEventBus().publishEntity(entity, entityType, Action.CHANGE);

        } catch (final IOException e) {
            logger.error(e);
            return putAndGetResponse(false);
        }
        return putAndGetResponse(entityExist);
    }

    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteNull() {
       return Response.status(Status.FORBIDDEN).build();
    }

    @DELETE
    @Path("{ENTITY_TYPE}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteAll(@PathParam("ENTITY_TYPE") String entityType) {

        final Entity FakeEntity = new Entity();

        if (Farm.class.getSimpleName().equalsIgnoreCase(entityType)) {
            final List<String> entityTypes = new ArrayList<>();
            entityTypes.add(VirtualHost.class.getSimpleName().toLowerCase());
            entityTypes.add(BackendPool.class.getSimpleName().toLowerCase());
            for (String fakeEntityType: entityTypes) {
                ((ApiApplication) application).getEventBus()
                    .publishEntity(FakeEntity, fakeEntityType, Action.DEL_ALL);
            }
            return deleteAndGetResponse(true);
        }

        ((ApiApplication) application).getEventBus()
            .publishEntity(FakeEntity, entityType, Action.DEL_ALL);
        return deleteAndGetResponse(true);
    }

    @DELETE
    @Path("{ENTITY_TYPE}/{ENTITY_ID}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    public Response delete(InputStream is) {
        String entityStr = "";
        String entityIdFromEntity = "";
        try {
            entityStr = convertStreamToString(is);
            final Entity entity = (Entity) JsonObject.fromJson(entityStr, Entity.class);
            entityIdFromEntity  = entity.getId();
            entityExist = isEntityExist(entityType, entityIdFromEntity);

            ((ApiApplication) application).getEventBus().publishEntity(entity, entityType, Action.DEL);

        } catch (final IOException e) {
            logger.error(e);
            return deleteAndGetResponse(false);
        }
        return deleteAndGetResponse(entityExist);
    }

    private String getEntity(String entityType, String id) {

        final Farm farm = ((ApiApplication) application).getFarm();
        String result = "";

        final EntityController entityController = farm.getEntityMap().get(entityType);

        if (entityController!=null) {
            result = entityController.get(id);
        }
        return result;
    }

    private boolean isEntityExist(String entityType, String id) {
        final String result = getEntity(entityType, id);
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
            final Writer writer = new StringWriter();

            final char[] buffer = new char[1024];
            try {
                final Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8")); //$NON-NLS-1$
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
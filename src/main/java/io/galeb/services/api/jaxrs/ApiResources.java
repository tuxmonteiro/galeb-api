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
import io.galeb.core.json.JsonObject;
import io.galeb.core.logging.Logger;
import io.galeb.core.model.Backend;
import io.galeb.core.model.BackendPool;
import io.galeb.core.model.Entity;
import io.galeb.core.model.Farm;
import io.galeb.core.model.Rule;
import io.galeb.core.model.VirtualHost;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    enum Method {
        GET,
        POST,
        PUT,
        DELETE,
        PATCH,
        OPTION
    }

    @Context UriInfo uriInfo;

    @Context Request request;

    @Context Application application;

    @DefaultValue("") @PathParam("ENTITY_TYPE") String entityType;

    @DefaultValue("") @PathParam("ENTITY_ID") String entityId;

    private boolean entityExist = false;

    private final Map<String, Class<? extends Entity>> mapEntityClass = new ConcurrentHashMap<>();

    public ApiResources() {
        mapEntityClass.put(Backend.class.getSimpleName().toLowerCase(), Backend.class);
        mapEntityClass.put(BackendPool.class.getSimpleName().toLowerCase(), BackendPool.class);
        mapEntityClass.put(Rule.class.getSimpleName().toLowerCase(), Rule.class);
        mapEntityClass.put(VirtualHost.class.getSimpleName().toLowerCase(), VirtualHost.class);
        mapEntityClass.put(Farm.class.getSimpleName().toLowerCase(), Farm.class);
    }

    private void logReceived(String uri, String body, Method method) {
        Logger logger = ((ApiApplication) application).getLogger();
        logger.info("[" + method.toString() + "] " + uri + ": " + body);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getNull() {
        logReceived("/", "", Method.GET);
        return Response.status(Status.FORBIDDEN).build();
    }

    @GET
    @Path("{ENTITY_TYPE}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("ENTITY_TYPE") String entityType) {
        logReceived("/" + entityType, "", Method.GET);
        final Farm farm = ((ApiApplication) application).getFarm();
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
        logReceived("/" + entityType + "/" + entityId, "", Method.GET);
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
        logReceived("/", "", Method.POST);
        return Response.status(Status.FORBIDDEN).build();
    }

    @POST
    @Path("{ENTITY_TYPE}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    public Response post(InputStream is) {
        String entityStr = "";
        final Class<?> clazz = getClass(entityType);
        final ApiApplication api = ((ApiApplication)application);
        final Logger logger = api.getLogger();

        try {
            if (clazz==null) {
                logger.error(entityType+" NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }
            if (entityType.equals(Farm.class.getSimpleName().toLowerCase())) {
                logger.error("POST /"+entityType+" not supported");
                return Response.status(Status.BAD_REQUEST).build();
            }

            entityStr = convertStreamToString(is);
            if (entityStr.isEmpty()) {
                logger.error("Json Body NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            final Entity entity = (Entity) JsonObject.fromJson(entityStr, clazz);

            api.getDistributedMap().getMap(clazz.getName()).putIfAbsent(entity.compoundId(), entity);

        } catch (final IOException|RuntimeException e) {
            logger.error(e);
            return Response.status(Status.BAD_REQUEST).build();
        }
        logReceived("/" + entityType, entityStr, Method.POST);
        return Response.accepted().build();
    }

    private Class<? extends Entity> getClass(String entityType) {
        return mapEntityClass.get(entityType);
    }

    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public Response putNull() {
        logReceived("/", "", Method.PUT);
        return Response.status(Status.FORBIDDEN).build();
    }

    @PUT
    @Path("{ENTITY_TYPE}/{ENTITY_ID}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    public Response put(InputStream is) {
        String entityStr = "";
        final Class<?> clazz = getClass(entityType);
        final ApiApplication api = ((ApiApplication)application);
        final Logger logger = api.getLogger();

        try {
            if (clazz==null) {
                logger.error(entityType+" NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            entityStr = convertStreamToString(is);
            if (entityStr.isEmpty()) {
                logger.error("Json Body NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            final Entity entity = (Entity) JsonObject.fromJson(entityStr, clazz);

            api.getDistributedMap().getMap(clazz.getName()).replace(entity.compoundId(), entity);

        } catch (final IOException|RuntimeException e) {
            logger.error(e);
            return Response.status(Status.BAD_REQUEST).build();
        }
        logReceived("/" + entityType + "/" + entityId, entityStr, Method.PUT);
        return Response.accepted().build();
    }

    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteNull() {
        logReceived("/", "", Method.DELETE);
        return Response.status(Status.FORBIDDEN).build();
    }

    @DELETE
    @Path("{ENTITY_TYPE}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteAll(@PathParam("ENTITY_TYPE") String entityType) {
        logReceived("/" + entityType, "", Method.DELETE);
        final Class<? extends Entity> clazz = getClass(entityType);
        final ApiApplication api = (ApiApplication)application;
        final Logger logger = api.getLogger();
        final Farm farm = api.getFarm();

        if (clazz==null) {
            logger.error(entityType+" NOT FOUND");
            return Response.status(Status.BAD_REQUEST).build();
        }

        final List<Class<? extends Entity>> arrayOfClasses = entityType.equals(Farm.class.getSimpleName().toLowerCase()) ?
                Arrays.asList(Backend.class, BackendPool.class, Rule.class, VirtualHost.class) : Arrays.asList(clazz);

        arrayOfClasses.stream().forEach(aclazz -> {
            farm.getCollection(aclazz).stream().forEach(entity -> {
                final ConcurrentMap<String, Entity> map = api.getDistributedMap().getMap(aclazz.getName());
                map.remove(entity.compoundId());
            });
        });

        return Response.accepted().build();
    }

    @DELETE
    @Path("{ENTITY_TYPE}/{ENTITY_ID}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    public Response delete(InputStream is) {
        String entityStr = "";
        final Class<?> clazz = getClass(entityType);
        final ApiApplication api = ((ApiApplication)application);
        final Logger logger = api.getLogger();

        try {
            if (clazz==null) {
                logger.error(entityType+" NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            entityStr = convertStreamToString(is);
            if (entityStr.isEmpty()) {
                logger.error("Json Body NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            final Entity entity = (Entity) JsonObject.fromJson(entityStr, clazz);
            final ConcurrentMap<String, Entity> map = api.getDistributedMap().getMap(clazz.getName());
            map.remove(entity.compoundId());

        } catch (final IOException e) {
            logger.error(e);
            return Response.status(Status.BAD_REQUEST).build();
        }
        logReceived("/" + entityType + "/" + entityId, entityStr, Method.DELETE);
        return Response.accepted().build();
    }

    private String getEntity(String entityType, String id) {
        final ApiApplication api = ((ApiApplication)application);
        final Farm farm = api.getFarm();
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
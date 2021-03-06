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

import io.galeb.core.json.JsonObject;
import io.galeb.core.model.Backend;
import io.galeb.core.model.BackendPool;
import io.galeb.core.model.Entity;
import io.galeb.core.model.Farm;
import io.galeb.core.model.Rule;
import io.galeb.core.model.VirtualHost;
import net.sf.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.cache.Cache;
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

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String VERSION = "3.1.11";

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

    private final Map<String, Class<? extends Entity>> mapEntityClass = new ConcurrentHashMap<>();

    public ApiResources() {
        mapEntityClass.put(Backend.class.getSimpleName().toLowerCase(), Backend.class);
        mapEntityClass.put(BackendPool.class.getSimpleName().toLowerCase(), BackendPool.class);
        mapEntityClass.put(Rule.class.getSimpleName().toLowerCase(), Rule.class);
        mapEntityClass.put(VirtualHost.class.getSimpleName().toLowerCase(), VirtualHost.class);
        mapEntityClass.put(Farm.class.getSimpleName().toLowerCase(), Farm.class);
    }

    private void logReceived(String uri, String body, Method method) {
        LOGGER.info("[" + method.toString() + "] " + uri + ": " + body);
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
    @SuppressWarnings("unchecked")
    public Response get(@PathParam("ENTITY_TYPE") String entityType) {
        final ApiApplication api = (ApiApplication) application;

        logReceived("/" + entityType, "", Method.GET);
        if (entityType.equals("version")) {
            return Response.ok("{ \"version\" : \"" + VERSION + "\" }").build();
        }
        Class<?> entityClass = getClass(entityType);
        if (entityClass != null) {
            if (entityType.equals("farm")) {
                return Response.ok("{ \"info\" : \"'GET /farm' was removed\" }").build();
            }
            String classFullName = entityClass.getName();
            Cache<String, String> cache;
            try {
                cache = api.getCache(classFullName);
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
                return Response.status(Status.SERVICE_UNAVAILABLE).build();
            }
            try {
                if (cache != null) {
                    Stream<Cache.Entry<String, String>> stream = StreamSupport.stream(cache.spliterator(), false);
                    return Response.ok("[" + stream
                            .map(Cache.Entry::getValue)
                            .collect(Collectors.joining(",")) + "]").build();
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
            }
        }
        return Response.status(Status.NOT_FOUND).build();
    }

    @GET
    @Path("{ENTITY_TYPE}/{ENTITY_ID}")
    @Produces(MediaType.APPLICATION_JSON)
    @SuppressWarnings("unchecked")
    public Response getOne() {
        final ApiApplication api = (ApiApplication) application;
        logReceived("/" + entityType + "/" + entityId, "", Method.GET);

        String classFullName = getClass(entityType).getName();
        Cache<String, String> cache;
        try {
            cache = api.getCache(classFullName);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
        }
        try {
            if (cache != null) {
                Stream<Cache.Entry<String, String>> stream = StreamSupport.stream(cache.spliterator(), false);
                String elements = stream.filter(entry -> entry.getKey().startsWith(entityId + Entity.SEP_COMPOUND_ID))
                      .map(Cache.Entry::getValue)
                      .collect(Collectors.joining(","));
                if (elements != null && !"".equals(elements)) {
                    return Response.ok("[" + elements + "]").build();
                }
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
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
    @SuppressWarnings("unchecked")
    public Response post(InputStream is) {
        String entityStr;
        final Class<?> clazz = getClass(entityType);
        final ApiApplication api = (ApiApplication)application;

        try {
            if (clazz==null) {
                LOGGER.error(entityType+" NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }
            if (entityType.equals(Farm.class.getSimpleName().toLowerCase())) {
                LOGGER.error("POST /"+entityType+" not supported");
                return Response.status(Status.BAD_REQUEST).build();
            }

            entityStr = convertStreamToString(is);
            if (entityStr.isEmpty()) {
                LOGGER.error("Json Body NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            final Entity entity = (Entity) JsonObject.fromJson(entityStr, clazz);
            entity.setEntityType(clazz.getSimpleName().toLowerCase());
            Cache<String, String> map = api.getCache(clazz.getName());
            map.putIfAbsent(entity.compoundId(), JsonObject.toJsonString(entity));
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            return Response.status(Status.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
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
    @SuppressWarnings("unchecked")
    public Response put(InputStream is) {
        String entityStr;
        final Class<?> clazz = getClass(entityType);
        final ApiApplication api = (ApiApplication)application;

        try {
            if (clazz==null) {
                LOGGER.error(entityType+" NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            entityStr = convertStreamToString(is);
            if (entityStr.isEmpty()) {
                LOGGER.error("Json Body NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            final Entity entity = (Entity) JsonObject.fromJson(entityStr, clazz);
            entity.setEntityType(clazz.getSimpleName().toLowerCase());
            Cache<String, String> map = api.getCache(clazz.getName());
            map.replace(entity.compoundId(), JsonObject.toJsonString(entity));
        } catch (final IOException e) {
            LOGGER.error(e.getMessage());
            return Response.status(Status.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
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
    @SuppressWarnings("unchecked")
    public Response deleteAll(@PathParam("ENTITY_TYPE") String entityType) {
        logReceived("/" + entityType, "", Method.DELETE);
        final Class<? extends Entity> clazz = getClass(entityType);
        final ApiApplication api = (ApiApplication)application;

        if (clazz==null) {
            LOGGER.error(entityType+" NOT FOUND");
            return Response.status(Status.BAD_REQUEST).build();
        }

        final List<Class<? extends Entity>> arrayOfClasses = entityType.equals(Farm.class.getSimpleName().toLowerCase()) ?
                Arrays.asList(Backend.class, BackendPool.class, Rule.class, VirtualHost.class) : Collections.singletonList(clazz);

        try {
            arrayOfClasses.stream().forEach(aclazz -> {
                final Cache<String, String> map = api.getCache(aclazz.getName());
                map.removeAll();
            });
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
        }

        return Response.accepted().build();
    }

    @DELETE
    @Path("{ENTITY_TYPE}/{ENTITY_ID}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    @SuppressWarnings("unchecked")
    public Response delete(InputStream is) {
        String entityStr;
        final Class<?> clazz = getClass(entityType);
        final ApiApplication api = (ApiApplication)application;

        try {
            if (clazz==null) {
                LOGGER.error(entityType+" NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            entityStr = convertStreamToString(is);
            if (entityStr.isEmpty()) {
                LOGGER.error("Json Body NOT FOUND");
                return Response.status(Status.BAD_REQUEST).build();
            }

            final Entity entity = (Entity) JsonObject.fromJson(entityStr, clazz);
            final Cache<String, String> map = api.getCache(clazz.getName());
            map.remove(entity.compoundId());
        } catch (final IOException e) {
            LOGGER.error(e.getMessage());
            return Response.status(Status.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
        }
        logReceived("/" + entityType + "/" + entityId, entityStr, Method.DELETE);
        return Response.accepted().build();
    }

    @GET
    @Path("info")
    @Produces(MediaType.APPLICATION_JSON)
    @SuppressWarnings("unchecked")
    public Response info() {
        long uptimeJVM = ManagementFactory.getRuntimeMXBean().getUptime();
        String uptime = getUptimeCommand();
        String version = getClass().getPackage().getImplementationVersion();
        String infoJson = new JSONObject().accumulate("uptime", uptime).accumulate("uptime-jvm", uptimeJVM).accumulate("version", version).toString();
        return Response.ok(infoJson).build();
    }

    public String getUptimeCommand() {
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", "uptime");
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            InputStream stream = process.getInputStream();
            return convertStreamToString(stream).replace("\n", "");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
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

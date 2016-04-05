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

package io.galeb.services.api.sched;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.galeb.core.cluster.ignite.IgniteCacheFactory;
import io.galeb.core.services.AbstractService;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.galeb.services.api.sched.SplitBrainCheckerScheduler.PROP_API_CHECK_SERVER;
import static io.galeb.services.api.sched.SplitBrainCheckerScheduler.PROP_API_PREFERRED_ZONE;


@DisallowConcurrentExecution
public class SplitBrainCheckerJob implements Job {

    private static final Logger LOGGER = LogManager.getLogger(SplitBrainCheckerJob.class);

    private static IgniteCacheFactory cacheFactory = IgniteCacheFactory.getInstance();

    private static Ignite ignite = (Ignite) cacheFactory.getClusterInstance();

    private final ObjectMapper mapper = new ObjectMapper();

    private final static AtomicInteger errorCounter = new AtomicInteger(0);

    private void init(final JobDataMap jobDataMap) {
        //
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        init(context.getJobDetail().getJobDataMap());

        LOGGER.info("=== " + this.getClass().getSimpleName() + " ===");

        String servers = System.getProperty(PROP_API_CHECK_SERVER);
        List<String> listOfServers = Arrays.asList(servers.split(","));

        List<String> localNodes = ignite.cluster().nodes().stream()
                .map(ClusterNode::id)
                .map(UUID::toString)
                .collect(Collectors.toList());

        listOfServers.stream().forEach(server -> {

            try {
                String path = "http://" + server + "/ignite?cmd=top";

                JsonNode json = getJson(path);

                if (json != null) {

                    List<String> remoteNodes = StreamSupport.stream(json.get("response").spliterator(), false)
                                                    .map(node -> node.get("nodeId").asText())
                                                    .collect(Collectors.toList());

                    if (localNodes.containsAll(remoteNodes)) {
                        LOGGER.info("Cluster OK! [ " + server + " ]");
                        errorCounter.set(0);
                        return;
                    }

                    boolean splitBrain = localNodes.stream().filter(remoteNodes::contains).count() == 0;

                    if (splitBrain) {
                        String preferred = System.getProperty(PROP_API_PREFERRED_ZONE);
                        int localNodesSize = localNodes.size();
                        int remoteNodesSize = remoteNodes.size();
                        if (localNodesSize < remoteNodesSize) {
                            LOGGER.error("Local cluster segment has " + localNodesSize + " nodes (remote has " + remoteNodesSize + " nodes)");
                            shutdownNodes();
                            return;
                        }
                        boolean isPreferred = preferred != null && Boolean.valueOf(preferred);
                        LOGGER.info("Local cluster preferred: " + (isPreferred ? "true" : "false"));
                        if (!isPreferred) {
                            shutdownNodes();
                            return;
                        }
                        warnSplitBrain();
                    }
                }
            } catch (URISyntaxException | IOException e) {
                LOGGER.error(e);
            }

        });

        LOGGER.debug("Job SplitBrainChecker done.");

    }

    private void warnSplitBrain() {
        LOGGER.warn("Split Brain!!! My Zone [ " +
                ignite.cluster().hostNames().stream().reduce((t, c) -> t + ", " + c).get() + " ] will remains UP");
    }

    private void shutdownNodes() {
        if (errorCounter.incrementAndGet() >= 2) {
            LOGGER.error("Split Brain!!! Shutting down my segment");
            ignite.cluster().stopNodes();
        }
    }

    private JsonNode getJson(String path) throws URISyntaxException, IOException {
        JsonNode json = null;
        RestTemplate restTemplate = new RestTemplate();
        URI uri = new URI(path);
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        try {
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            boolean result = response.getStatusCode().value() < 400;

            if (result) {
                json = mapper.readTree(response.getBody());
            }
        } catch (RestClientException e) {
            LOGGER.error("Split Brain Check FAILED. " + e.getMessage());
        }
        return json;
    }


}

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
import io.galeb.core.logging.Logger;
import io.galeb.core.services.AbstractService;
import io.galeb.services.api.Api;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterNode;
import org.quartz.*;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


@DisallowConcurrentExecution
public class SplitBrainCheckerJob implements Job {

    private Optional<Logger> logger = Optional.empty();
    private static IgniteCacheFactory cacheFactory = IgniteCacheFactory.INSTANCE;

    private static Ignite ignite = (Ignite) cacheFactory.getClusterInstance();

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private void init(final JobDataMap jobDataMap) {
        if (!logger.isPresent()) {
            logger = Optional.ofNullable((Logger) jobDataMap.get(AbstractService.LOGGER));
        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        init(context.getJobDetail().getJobDataMap());

        logger.ifPresent(log -> log.info("=== " + this.getClass().getSimpleName() + " ==="));

        try {
            JsonNode json = getJson();

            if (json != null) {
                List<String> localNodes = ignite.cluster().nodes().stream()
                                                .map(ClusterNode::id)
                                                .map(UUID::toString)
                                                .collect(Collectors.toList());
                List<String> remoteNodes = StreamSupport.stream(json.get("response").spliterator(), false)
                                                .map(node -> node.get("nodeId").asText())
                                                .collect(Collectors.toList());

                if (localNodes.containsAll(remoteNodes)) {
                    logger.ifPresent(log -> log.info("Cluster OK!"));
                    return;
                }

                boolean exist = localNodes.stream().filter(remoteNodes::contains).count() > 0;

                if (!exist) {
                    if (localNodes.size() < remoteNodes.size()) {
                        ignite.cluster().stopNodes();
                    }
                }
            }
        } catch (URISyntaxException | IOException e) {
            logger.ifPresent(log -> log.error(e));
        }

        logger.ifPresent(log -> log.debug("Job SplitBrainChecker done."));

    }

    private JsonNode getJson() throws URISyntaxException, IOException {
        String path = "http://" + System.getProperty(Api.PROP_API_CHECK_SERVER, "localhost:9010") + "/ignite?cmd=top";
        JsonNode json = null;
        RestTemplate restTemplate = new RestTemplate();
        URI uri = new URI(path);
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        boolean result = response.getStatusCode().value() < 400;

        if (result) {
            json = mapper.readTree(response.getBody());
        }
        return json;
    }


}

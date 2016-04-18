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

package io.galeb.services.api;

import io.galeb.core.cluster.ignite.IgniteCacheFactory;
import io.galeb.core.cluster.ignite.IgniteClusterLocker;
import io.galeb.core.services.AbstractService;
import io.galeb.services.api.jaxrs.ApiApplication;
import io.galeb.services.api.sched.SplitBrainCheckerScheduler;
import io.galeb.undertow.jaxrs.Deployer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

public class Api extends AbstractService {

    private static final Logger LOGGER = LogManager.getLogger();

    public static final  String PROP_API_PREFIX    = Api.class.getPackage().getName() + ".";

    private static final String PROP_API_BIND      = PROP_API_PREFIX + "bind";

    private static final String PROP_API_PORT      = PROP_API_PREFIX + "port";

    private static final String PROP_API_IOTHREADS = PROP_API_PREFIX + "iothread";

    public static final  int    DEFAULT_PORT       = 9090;

    private final SplitBrainCheckerScheduler splitBrainCheckerScheduler = new SplitBrainCheckerScheduler();

    static {
        if (System.getProperty(PROP_API_PORT)==null) {
            System.setProperty(PROP_API_PORT, Integer.toString(DEFAULT_PORT));
        }
        if (System.getProperty(PROP_API_IOTHREADS)==null) {
            System.setProperty(PROP_API_IOTHREADS, String.valueOf(Runtime.getRuntime().availableProcessors()));
        }
    }

    public Api() {
        super();
    }

    @PostConstruct
    public void init() {
        cacheFactory = IgniteCacheFactory.getInstance().start();
        clusterLocker = IgniteClusterLocker.getInstance().start();

        splitBrainCheckerScheduler.setFarm(farm).start();

        final String bind = System.getProperty(PROP_API_BIND, "0.0.0.0");
        final int port = Integer.parseInt(System.getProperty(PROP_API_PORT));
        final String iothreads = System.getProperty(PROP_API_IOTHREADS);

        final Map<String, String> options = new HashMap<>();
        options.put("IoThreads", iothreads);

        new Deployer().setHost(bind)
                      .setPort(port)
                      .deploy(new ApiApplication().setManager(this))
                      .setOptions(options)
                      .start();

        LOGGER.info(String.format("API [%s:%d] ready", bind, port));
    }

}

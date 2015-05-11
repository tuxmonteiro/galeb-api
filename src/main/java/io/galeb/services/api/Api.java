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

import io.galeb.core.controller.EntityController.Action;
import io.galeb.core.json.JsonObject;
import io.galeb.core.services.AbstractService;
import io.galeb.services.api.jaxrs.ApiApplication;
import io.galeb.undertow.jaxrs.Deployer;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

public class Api extends AbstractService {

    private static final String PROP_MANAGER_PREFIX    = Api.class.getPackage().getName()+".";

    private static final String PROP_MANAGER_PORT      = PROP_MANAGER_PREFIX+"port";

    private static final String PROP_MANAGER_IOTHREADS = PROP_MANAGER_PREFIX+"iothread";

    public static final int     DEFAULT_PORT           = 9090;

    static {
        if (System.getProperty(PROP_MANAGER_PORT)==null) {
            System.setProperty(PROP_MANAGER_PORT, Integer.toString(DEFAULT_PORT));
        }
        if (System.getProperty(PROP_MANAGER_IOTHREADS)==null) {
            System.setProperty(PROP_MANAGER_IOTHREADS, String.valueOf(Runtime.getRuntime().availableProcessors()));
        }
    }

    public Api() {
        super();
    }

    @PostConstruct
    protected void init() {

        super.prelaunch();

        int port = Integer.parseInt(System.getProperty(PROP_MANAGER_PORT));
        String iothreads = System.getProperty(PROP_MANAGER_IOTHREADS);

        final Map<String, String> options = new HashMap<>();
        options.put("IoThreads", iothreads);

        new Deployer().setHost("0.0.0.0")
                      .setPort(port)
                      .deploy(new ApiApplication().setManager(this))
                      .setOptions(options)
                      .start();

        logger.debug(String.format("[0.0.0.0:%d] ready", port));
    }

    @Override
    public void handleController(JsonObject json, Action action) {
        // future
    }

}

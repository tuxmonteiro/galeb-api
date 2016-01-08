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

import io.galeb.core.jcache.CacheFactory;
import io.galeb.core.logging.Logger;
import io.galeb.services.api.Api;

import java.util.HashSet;
import java.util.Set;

import javax.cache.Cache;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/")
public class ApiApplication extends Application {

    private Logger logger;

    private CacheFactory cacheFactory;

    public Application setManager(final Api api) {
        logger = api.getLogger();
        cacheFactory = api.getCacheFactory();
        return this;
    }

    @Override
    public Set<Class<?>> getClasses() {
        final Set<Class<?>> classes = new HashSet<>();
        classes.add(ApiResources.class);
        return classes;
    }

    public Logger getLogger() {
        return logger;
    }

    public Cache getCache(String key) {
        return cacheFactory.getCache(key);
    }

}

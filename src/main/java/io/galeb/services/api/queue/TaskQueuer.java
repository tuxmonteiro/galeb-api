/*
 * Copyright (c) 2014-2015 Globo.com
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

package io.galeb.services.api.queue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class TaskQueuer {

    private static final ExecutorService EXECUTOR = Executors.newWorkStealingPool();
    private static final String          QUEUE_LIMIT_DEFAULT = String.valueOf(4 * 1024);
    private static final String          PROP_QUEUE_LIMIT    = TaskQueuer.class.getPackage().getName()+".limit";
    private static final int             QUEUE_LIMIT;

    private static final AtomicReferenceArray<Future<Integer>> taskList;

    static {
        QUEUE_LIMIT = Integer.parseInt(System.getProperty(PROP_QUEUE_LIMIT, QUEUE_LIMIT_DEFAULT));
        taskList = new AtomicReferenceArray<>(QUEUE_LIMIT);
    }

    private TaskQueuer() {
        //
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
    }

    public static synchronized Future<Integer> push(Callable<Integer> callable) throws RuntimeException {
        final Random randomGenerator = new Random();
        int randomPos = randomGenerator.nextInt(QUEUE_LIMIT - 1);
        Future<Integer> status = submitTaskIfPossible(callable, randomPos);
        if (status != null) {
            return status;
        }

        Set<Integer> randomHistory = new HashSet<>();
        randomHistory.add(randomPos);
        for (int tryLimit = 0; tryLimit < QUEUE_LIMIT * 2; ++tryLimit) {
            randomPos = randomGenerator.nextInt(QUEUE_LIMIT - 1);
            if (!randomHistory.add(randomPos)) {
                continue;
            }
            status = submitTaskIfPossible(callable, randomPos);
            if (status != null) {
                return status;
            }
        }

        throw new UnsupportedOperationException("Queue is FULL");
    }

    private static Future<Integer> submitTaskIfPossible(Callable<Integer> callable, int pos) {
        Future<Integer> status = taskList.get(pos);
        if (status == null || status.isDone() || status.isCancelled()) {
            status = EXECUTOR.submit(callable);
            taskList.set(pos, status);
            return status;
        }
        return null;
    }
}

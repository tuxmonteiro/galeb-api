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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class TaskQueuer {

    private static final ScheduledExecutorService SCHEDULER           = Executors.newScheduledThreadPool(1);
    private static final ExecutorService          QUEUE               = Executors.newWorkStealingPool();
    private static final String                   QUEUE_LIMIT_DEFAULT = String.valueOf(4 * 1024);
    private static final String                   PROP_QUEUE_LIMIT    = TaskQueuer.class.getPackage().getName()+".limit";

    private static final AtomicReferenceArray<Future<Integer>> taskList;
    private static final AtomicInteger lastEmptySlot = new AtomicInteger(0);

    static {
        final int queueLimit = Integer.parseInt(System.getProperty(PROP_QUEUE_LIMIT,QUEUE_LIMIT_DEFAULT));
        taskList = new AtomicReferenceArray<>(queueLimit);
        SCHEDULER.scheduleAtFixedRate(TaskQueuer::taskListCleaner, 0, 5, TimeUnit.SECONDS);
    }

    private static synchronized void taskListCleaner() {
        for (int pos = 0; pos < taskList.length(); pos++) {
            Future<Integer> task = taskList.get(pos);
            if (task == null) {
                continue;
            }
            if (task.isDone() || task.isCancelled()) {
                taskList.set(pos, null);
                lastEmptySlot.set(pos);
            }
        }
    }

    public static void shutdown() {
        SCHEDULER.shutdown();
        QUEUE.shutdown();
    }

    public static synchronized Future<Integer> push(Callable<Integer> callable) throws RuntimeException {
        Future<Integer> newTask = null;
        if (taskList.get(lastEmptySlot.get()) == null) {
            newTask = QUEUE.submit(callable);
            taskList.set(lastEmptySlot.get(), newTask);
        } else {
            for (int pos = 0; pos < taskList.length(); pos++) {
                if (taskList.get(pos) == null) {
                    newTask =  QUEUE.submit(callable);
                    taskList.set(pos, newTask);
                    break;
                }
            }
            if (newTask == null) {
                throw new UnsupportedOperationException("Queue is FULL");
            }
        }
        return newTask;
    }
}

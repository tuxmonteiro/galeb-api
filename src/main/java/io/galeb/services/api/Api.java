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

import io.galeb.core.services.AbstractService;
import io.galeb.services.api.jaxrs.ApiApplication;
import io.galeb.services.api.sched.SplitBrainCheckerJob;
import io.galeb.undertow.jaxrs.Deployer;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

public class Api extends AbstractService implements JobListener {

    private static final String PROP_API_PREFIX         = Api.class.getPackage().getName() + ".";

    private static final String PROP_API_PORT           = PROP_API_PREFIX + "port";

    private static final String PROP_API_IOTHREADS      = PROP_API_PREFIX + "iothread";

    public static final String PROP_API_CHECK_INTERVAL = PROP_API_PREFIX + "splitbrain.check.interval";

    public static final String PROP_API_CHECK_SERVER   = PROP_API_PREFIX + "splitbrain.check.server";

    public static final int     DEFAULT_PORT                = 9090;

    private Scheduler scheduler;

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

        super.prelaunch();
        setupScheduler();
        startJobs();

        int port = Integer.parseInt(System.getProperty(PROP_API_PORT));
        String iothreads = System.getProperty(PROP_API_IOTHREADS);

        final Map<String, String> options = new HashMap<>();
        options.put("IoThreads", iothreads);

        new Deployer().setHost("0.0.0.0")
                      .setPort(port)
                      .deploy(new ApiApplication().setManager(this))
                      .setOptions(options)
                      .start();

        logger.debug(String.format("[0.0.0.0:%d] ready", port));
    }

    private void setupScheduler() {
        try {
            scheduler = new StdSchedulerFactory().getScheduler();
            scheduler.getListenerManager().addJobListener(this);

            scheduler.start();
        } catch (SchedulerException e) {
            logger.error(e);
        }
    }

    private void startJobs() {
        try {
            if (scheduler.isStarted()) {
                int interval = Integer.parseInt(System.getProperty(PROP_API_CHECK_INTERVAL, "10000"));
                startChecker(interval);
            }
        } catch (SchedulerException e) {
            logger.error(e);
        }
    }

    private void startChecker(int interval) throws SchedulerException {
        Trigger triggerCheckerJob = newTrigger().withIdentity(UUID.randomUUID().toString())
                .startNow()
                .withSchedule(simpleSchedule().withIntervalInMilliseconds(interval).repeatForever())
                .build();

        JobDataMap jobdataMap = new JobDataMap();
        jobdataMap.put(AbstractService.FARM, farm);
        jobdataMap.put(AbstractService.LOGGER, logger);

        JobDetail checkerJob = newJob(SplitBrainCheckerJob.class).withIdentity(SplitBrainCheckerJob.class.getName())
                .setJobData(jobdataMap)
                .build();

        scheduler.scheduleJob(checkerJob, triggerCheckerJob);
    }

    @Override
    public String getName() { return toString(); }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        logger.debug(context.getJobDetail().getKey().getName()+" to be executed");
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        logger.debug(context.getJobDetail().getKey().getName()+" vetoed");
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        logger.debug(context.getJobDetail().getKey().getName()+" was executed");
    }
}

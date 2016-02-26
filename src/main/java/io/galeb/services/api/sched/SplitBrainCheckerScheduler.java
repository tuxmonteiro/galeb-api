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

import io.galeb.core.logging.Logger;
import io.galeb.core.model.Farm;
import io.galeb.core.services.AbstractService;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.UUID;

import static io.galeb.services.api.Api.PROP_API_PREFIX;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

public class SplitBrainCheckerScheduler implements JobListener {

    public static final String PROP_API_CHECK_ENABLE   = PROP_API_PREFIX + "splitbrain.check.enable";
    public static final String PROP_API_CHECK_INTERVAL = PROP_API_PREFIX + "splitbrain.check.interval";
    public static final String PROP_API_CHECK_SERVER   = PROP_API_PREFIX + "splitbrain.check.server";
    public static final String PROP_API_PREFERRED_ZONE = PROP_API_PREFIX + "splitbrain.preferred.zone";

    private static final String CHECK_ENABLE_DEFAULT   = "false";
    private static final String CHECK_INTERVAL_DEFAULT = "10000";
    private static final String CHECK_SERVER_DEFAULT   = "localhost:9010";

    static {
        if (System.getProperty(PROP_API_CHECK_ENABLE) == null) {
            System.setProperty(PROP_API_CHECK_ENABLE, CHECK_ENABLE_DEFAULT);
        }
        if (System.getProperty(PROP_API_CHECK_INTERVAL) == null) {
            System.setProperty(PROP_API_CHECK_INTERVAL, CHECK_INTERVAL_DEFAULT);
        }
        if (System.getProperty(PROP_API_CHECK_SERVER) == null) {
            System.setProperty(PROP_API_CHECK_SERVER, CHECK_SERVER_DEFAULT);
        }
    }

    private Logger logger;
    private Scheduler scheduler;
    private Farm farm;

    public SplitBrainCheckerScheduler setFarm(final Farm farm) {
        this.farm = farm;
        return this;
    }

    public SplitBrainCheckerScheduler setLogger(final Logger logger) {
        this.logger = logger;
        return this;
    }

    public void start() {
        String enableCheckProp = System.getProperty(PROP_API_CHECK_ENABLE);
        boolean enableCheck = enableCheckProp != null && Boolean.valueOf(enableCheckProp);
        if (enableCheck) {
            setupScheduler();
            startJobs();
        }
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
                int interval = Integer.parseInt(System.getProperty(SplitBrainCheckerScheduler.PROP_API_CHECK_INTERVAL));
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

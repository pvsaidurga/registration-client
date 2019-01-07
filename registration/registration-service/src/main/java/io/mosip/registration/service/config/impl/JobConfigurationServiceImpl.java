package io.mosip.registration.service.config.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.dao.SyncJobConfigDAO;
import io.mosip.registration.dto.ResponseDTO;
import io.mosip.registration.dto.SuccessResponseDTO;
import io.mosip.registration.entity.SyncJobDef;
import io.mosip.registration.jobs.BaseJob;
import io.mosip.registration.service.BaseService;
import io.mosip.registration.service.config.JobConfigurationService;

/**
 * implementation class of {@link JobConfigurationService}
 * 
 * @author YASWANTH S
 *
 */
@Service
public class JobConfigurationServiceImpl extends BaseService implements JobConfigurationService {

	@Autowired
	private SyncJobConfigDAO jobConfigDAO;

	/**
	 * Sheduler factory bean which will take Job and Trigger details and run jobs
	 * implicitly
	 */
	@Autowired
	private SchedulerFactoryBean schedulerFactoryBean;

	/**
	 * LOGGER for logging
	 */
	private static final Logger LOGGER = AppConfig.getLogger(JobConfigurationServiceImpl.class);

	/**
	 * sync job map with key as jobID and value as SyncJob (Entity)
	 */
	private   Map<String, SyncJobDef> SYNC_JOB_MAP = new HashMap<>();

	private List<SyncJobDef> jobList;

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.service.JobConfigurationService#initiateJobs()
	 */
	@PostConstruct
	public void initiateJobs() {
		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Jobs initiation was started");

		jobList = jobConfigDAO.getActiveJobs();
		jobList.forEach(syncJob -> SYNC_JOB_MAP.put(syncJob.getId(), syncJob));

		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Jobs initiation was completed");

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.service.JobConfigurationService#startJobs(org.
	 * springframework.context.ApplicationContext)
	 */
	@SuppressWarnings("unchecked")
	public ResponseDTO startScheduler(ApplicationContext applicationContext) {
		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "start jobs invocation started");

		ResponseDTO responseDTO = new ResponseDTO();
		
		
		
			try {
				/** Clear scheduler Before Starting */
				schedulerFactoryBean.getScheduler().clear();
			} catch (SchedulerException e) {
			
				/** Error Response */
				setErrorResponse(responseDTO, RegistrationConstants.START_SCHEDULER_ERROR_MESSAGE,null);
				return responseDTO;
			
			}
		
		
		
	
		Map<String, Object> jobDataAsMap = new HashMap<>();
		jobDataAsMap.put("applicationContext", applicationContext);
		jobDataAsMap.putAll(SYNC_JOB_MAP);
		
		JobDataMap jobDataMap =new JobDataMap(jobDataAsMap);		

		SYNC_JOB_MAP.forEach((jobId, syncJob) -> {
			try {
				if (syncJob.getParentSyncJobId() == null) {

					BaseJob baseJob = null;
					
					// Get Job instance through application context
					baseJob = (BaseJob) applicationContext.getBean(syncJob.getApiName());

					JobDetail jobDetail = JobBuilder.newJob(baseJob.jobClass()).withIdentity(syncJob.getId())
							.usingJobData(jobDataMap).build();

					CronTrigger trigger = (CronTrigger) TriggerBuilder.newTrigger().forJob(jobDetail)
							.withIdentity(syncJob.getId())
							.withSchedule(CronScheduleBuilder.cronSchedule(syncJob.getSyncFrequency())).build();

					schedulerFactoryBean.getScheduler().scheduleJob(jobDetail, trigger);
					
					setSuccessResponse(responseDTO, RegistrationConstants.BATCH_JOB_START_SUCCESS_MESSAGE,null);

				}
			} catch (SchedulerException | NoSuchBeanDefinitionException exception) {
				LOGGER.error(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID, exception.getMessage());
				
				/** Error Response */
				setErrorResponse(responseDTO, RegistrationConstants.START_SCHEDULER_ERROR_MESSAGE,null);
			} 
		});
		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "start jobs invocation ended");

		return responseDTO;

	}

	public ResponseDTO stopScheduler(boolean shutdown) {
		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "stop jobs invocation started");

		ResponseDTO responseDTO = new ResponseDTO();
		try {
			schedulerFactoryBean.getScheduler().shutdown(shutdown);
			setSuccessResponse(responseDTO, RegistrationConstants.BATCH_JOB_STOP_SUCCESS_MESSAGE,null);

		} catch (SchedulerException schedulerException) {
			LOGGER.error(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, schedulerException.getMessage());
			
			setErrorResponse(responseDTO, RegistrationConstants.STOP_SCHEDULER_ERROR_MESSAGE,null);
		}
		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "stop jobs invocation ended");

		return responseDTO;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.service.JobConfigurationService#
	 * getCurrentRunningJobDetails()
	 */
	public ResponseDTO getCurrentRunningJobDetails() {
		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "get current running job details started");

		ResponseDTO responseDTO = new ResponseDTO();
		try {

			// Get currently executing jobs from scheduler factory
			List<JobExecutionContext> executingJobList = schedulerFactoryBean.getScheduler()
					.getCurrentlyExecutingJobs();
			SuccessResponseDTO successResponseDTO = new SuccessResponseDTO();
			Map<String, Object> jobNames = new HashMap<>();
			for (JobExecutionContext jobExecutionContext : executingJobList) {

				// @see Need to be prepared as per businness requirement
				jobNames.put("jobName", jobExecutionContext.getJobDetail());
			}
			successResponseDTO.setOtherAttributes(jobNames);
			responseDTO.setSuccessResponseDTO(successResponseDTO);

		} catch (SchedulerException schedulerException) {
			LOGGER.error(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, schedulerException.getMessage());
			
			setErrorResponse(responseDTO, RegistrationConstants.CURRENT_JOB_DETAILS_ERROR_MESSAGE,null);

		}

		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "get current running job details ended");

		return responseDTO;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.registration.service.JobConfigurationService#executeJob(java.lang.
	 * String)
	 */
	@Override
	public ResponseDTO executeJob(ApplicationContext applicationContext, String jobId) {

		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Execute job started");
		ResponseDTO responseDTO = null;
		try {
			
			SyncJobDef syncJobDef= SYNC_JOB_MAP.get(jobId);
			
			
			// Get Job using application context and api name
			BaseJob job = (BaseJob) applicationContext.getBean(syncJobDef.getApiName());

			String triggerPoint = SessionContext.getInstance().getUserContext().getUserId();
			
			// Job Invocation
			responseDTO = job.executeJob(triggerPoint,jobId);
			
		} catch (NoSuchBeanDefinitionException | NullPointerException |IllegalArgumentException exception) {
			LOGGER.error(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, exception.getMessage());
			
			responseDTO = new ResponseDTO();
			setErrorResponse(responseDTO, RegistrationConstants.EXECUTE_JOB_ERROR_MESSAGE,null);
		} 
		LOGGER.debug(RegistrationConstants.BATCH_JOBS_CONFIG_LOGGER_TITLE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Execute job ended");
		return responseDTO;
	}

	


}

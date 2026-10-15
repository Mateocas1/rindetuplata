package ar.rindetuplata.ingest;

import ar.rindetuplata.alert.AlertEvaluationListener;
import ar.rindetuplata.store.RunMode;
import java.time.LocalDate;
import java.util.function.BiConsumer;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code dailyIngestionJob}: one step per data kind, in the order the kinds are ranked. Each step is a tasklet that
 * reads {@code dataDate} and {@code mode} from its job parameters, so a restart re-runs only the steps that did not
 * finish. Steps never fail because a source failed: {@link IngestionSteps} records that as a FAILED
 * {@code source_run} row and the job still COMPLETES (S5).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestionProperties.class)
class DailyIngestionJobConfiguration {

	@Bean
	Job dailyIngestionJob(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			IngestionSteps steps, RebuildHookListener rebuildHook, AlertEvaluationListener alertEvaluation) {
		return new JobBuilder(IngestionService.JOB_NAME, jobRepository)
			.start(step("plazoFijoStep", jobRepository, transactionManager, steps::plazoFijo))
			.next(step("fundsStep", jobRepository, transactionManager, steps::funds))
			.next(step("cryptoStep", jobRepository, transactionManager, steps::crypto))
			.next(step("macroStep", jobRepository, transactionManager, steps::macro))
			.next(step("dollarStep", jobRepository, transactionManager, steps::dollar))
			.next(step("walletsStep", jobRepository, transactionManager, steps::wallets))
			.listener(rebuildHook)
			.listener(alertEvaluation)
			.build();
	}

	private static Step step(String name, JobRepository jobRepository, PlatformTransactionManager transactionManager,
			BiConsumer<LocalDate, RunMode> body) {
		Tasklet tasklet = (contribution, chunkContext) -> {
			JobParameters parameters = contribution.getStepExecution().getJobParameters();
			LocalDate dataDate = LocalDate.parse(parameters.getString(IngestionService.DATA_DATE_PARAMETER));
			RunMode mode = RunMode.valueOf(parameters.getString(IngestionService.MODE_PARAMETER));
			body.accept(dataDate, mode);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder(name, jobRepository).tasklet(tasklet, transactionManager).build();
	}
}

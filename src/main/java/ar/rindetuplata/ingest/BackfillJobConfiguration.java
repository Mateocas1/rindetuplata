package ar.rindetuplata.ingest;

import java.time.LocalDate;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code historyBackfillJob(from, to)} (T4a, L33 a): fills the stored history of the days {@code from..to}, both
 * inclusive. Steps: the dated FCI endpoint (one transaction per day, with a cursor in the step's execution context so
 * a restart resumes at the next day instead of starting over), the ArgentinaDatos MEP series (read once), and BCRA
 * macro by date range (once per variable). Its only listener hands out the job id; there is no alert or rebuild
 * listener (see {@link BackfillJobListener}).
 */
@Configuration(proxyBeanMethods = false)
class BackfillJobConfiguration {

	static final String FROM_PARAMETER = "from";

	static final String TO_PARAMETER = "to";

	/** The next day the funds step has to fetch, in the step's execution context. */
	static final String CURSOR = "nextDay";

	/** The rows the run inserted so far, in the job's execution context, for the summary row. */
	static final String INSERTED = "inserted";

	@Bean
	BackfillJobListener backfillJobListener() {
		return new BackfillJobListener();
	}

	@Bean
	Job historyBackfillJob(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			BackfillSteps steps, BackfillJobListener listener) {
		return new JobBuilder(BackfillService.JOB_NAME, jobRepository)
			.start(new StepBuilder("backfillFundsStep", jobRepository).tasklet(fundsTasklet(steps), transactionManager)
				.build())
			.next(step("backfillDollarStep", jobRepository, transactionManager, (from, to, execution) -> steps.dollar(from, to)))
			.next(step("backfillMacroStep", jobRepository, transactionManager, (from, to, execution) -> steps.macro(from, to)))
			.next(step("backfillSummaryStep", jobRepository, transactionManager, (from, to, execution) -> {
				steps.summary(from, to, insertedSoFar(execution));
				return 0;
			}))
			.listener(listener)
			.build();
	}

	/** One call of the tasklet is one day, so each day commits on its own and the cursor survives a crash. */
	private static Tasklet fundsTasklet(BackfillSteps steps) {
		return (contribution, chunkContext) -> {
			StepExecution execution = contribution.getStepExecution();
			LocalDate from = date(execution.getJobParameters(), FROM_PARAMETER);
			LocalDate to = date(execution.getJobParameters(), TO_PARAMETER);
			ExecutionContext context = execution.getExecutionContext();
			LocalDate day = context.containsKey(CURSOR) ? LocalDate.parse(context.getString(CURSOR)) : from;
			if (day.isAfter(to)) {
				return RepeatStatus.FINISHED;
			}
			addInserted(execution, steps.fundsDay(day, day.isAfter(from)));
			context.putString(CURSOR, day.plusDays(1).toString());
			return day.isBefore(to) ? RepeatStatus.CONTINUABLE : RepeatStatus.FINISHED;
		};
	}

	private static Step step(String name, JobRepository jobRepository, PlatformTransactionManager transactionManager,
			RangeBody body) {
		Tasklet tasklet = (contribution, chunkContext) -> {
			JobParameters parameters = contribution.getStepExecution().getJobParameters();
			StepExecution execution = contribution.getStepExecution();
			addInserted(execution, body.run(date(parameters, FROM_PARAMETER), date(parameters, TO_PARAMETER), execution));
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder(name, jobRepository).tasklet(tasklet, transactionManager).build();
	}

	private static long insertedSoFar(StepExecution execution) {
		return execution.getJobExecution().getExecutionContext().getLong(INSERTED, 0L);
	}

	private static void addInserted(StepExecution execution, long rows) {
		execution.getJobExecution().getExecutionContext().putLong(INSERTED, insertedSoFar(execution) + rows);
	}

	private static LocalDate date(JobParameters parameters, String name) {
		return LocalDate.parse(parameters.getString(name));
	}

	/** A step over the whole range; answers the rows it inserted. */
	@FunctionalInterface
	private interface RangeBody {

		long run(LocalDate from, LocalDate to, StepExecution execution);
	}
}

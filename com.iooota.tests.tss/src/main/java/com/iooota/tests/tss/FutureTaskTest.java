package com.iooota.tests.tss;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iooota.util.PrintHelper;
import com.iooota.util.basic.logging.ExceptionUtils;
import com.iooota.util.threads.FutureHelper;
import com.iooota.util.threads.IoootaThreadPoolManager;

public class FutureTaskTest
{

	private static final Logger log = LoggerFactory.getLogger(FutureTaskTest.class);
	private static final ExecutorService exec = IoootaThreadPoolManager
	.priorityTasksExecutor(FutureTaskTest.class.getSimpleName());

	public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException
	{
		Future<Integer> fut = CompletableFuture.completedFuture(null);
		PrintHelper.out("Running an already completed future...");
		PrintHelper.out("Task is done: {}; returned value: {}\n", fut.isDone(), fut.get(1, TimeUnit.SECONDS));

		Integer res;
		FutureTask<Integer> instantTask = FutureHelper.completedFutureTask(1);
		PrintHelper.out("Running a single instruction FutureTask, waiting zero for result");
		res = instantTask.get(0, TimeUnit.SECONDS);
		PrintHelper.out("Task is done: {}; returned value: {}\n", instantTask.isDone(), res);

		FutureTask<Integer> futTask = new FutureTask<>(() ->
		{
			TimeUnit.SECONDS.sleep(10);
			return 5;
		});

		exec.submit(futTask);

		TimeUnit.SECONDS.sleep(5);
		PrintHelper.out("Task is done: {}; task is canceled: {}", futTask.isDone(), futTask.isCancelled());
		PrintHelper.out("Waiting for the result...");
		res = futTask.get();
		PrintHelper.out("Retrieved result: {}\n", res);

		PrintHelper.out("Running againg, this time we won't wait");
		res = null;
		futTask = new FutureTask<>(() ->
		{
			TimeUnit.SECONDS.sleep(10);
			return 5;
		});
		exec.submit(futTask);
		TimeUnit.SECONDS.sleep(5);
		futTask.cancel(true);
		PrintHelper.out("Task is done: {}; task is canceled: {}", futTask.isDone(), futTask.isCancelled());
		try
		{
			res = futTask.get();
		}
		catch (Exception e)
		{
			ExceptionUtils.logError(log, e, "Failure retrieving task result: {}", e);
		}
		PrintHelper.out("Retrieved result: {}", res);
	}

}

package com.iooota.tests.tss;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TSSTestFt
{
	private final static ExecutorService pool = Executors.newCachedThreadPool();
	// private final static ExecutorService pool =
	// Executors.newWorkStealingPool();
	// private static final ExecutorService pool = new ThreadPoolExecutor(0, 8,
	// 5, TimeUnit.SECONDS,
	// new ArrayBlockingQueue<>(20, true));

	public TSSTestFt()
	{
	}

	public static void main(String[] args) throws InterruptedException, ExecutionException
	{
		int thNum = 500;
		if (args.length > 0)
			thNum = Integer.parseInt(args[0]);
		System.out.println("Starting test with " + thNum + " threads");
		List<Future<Long>> runs = new ArrayList<Future<Long>>(thNum);
		int iters = 200;
		int periodSec = 2;
		for (int i = 0; i < iters; i++)
		{
			buildThreads(thNum, runs);
			System.out.println(
			i + ": " + thNum + " completable future created, sleeping " + periodSec + "s before consuming them...");
			TimeUnit.SECONDS.sleep(periodSec);
			consumeThreads(runs);
			if (i < (iters - 1))
				System.out.println(i + ": " + thNum + " completable future completed, starting all over again...");
		}
		System.out.println("Iterations done, nulling variables, forcing GC and sleeping a bit before stopping...");
		runs.clear();
		runs = null;
		System.gc();
		TimeUnit.MINUTES.sleep(2);
		System.out.println("Stopping.");
	}

	private static void consumeThreads(List<Future<Long>> runs)
	throws InterruptedException, ExecutionException
	{
		List<Long> results = new ArrayList<>(runs.size());
		for (Future<Long> r : runs)
		{
			try
			{
				results.add(r.get(1, TimeUnit.MILLISECONDS));
			}
			catch (TimeoutException e)
			{
				System.err.println("Producer thread did not respond in time");
				r.cancel(true);
			}
		}
		results.clear();
		runs.clear();
	}

	@SuppressWarnings("unchecked")
	private static void buildThreads(int thNum, List<Future<Long>> runs)
	{
		runs.clear();
		for (int i = 0; i < thNum; i++)
		{
			final FutureTask<Long> task = new FutureTask<Long>(() ->
			{
				try
				{
					long id = Thread.currentThread().getId();
					TimeUnit.HOURS.sleep(1);
					return id;
				}
				catch (Exception e)
				{
					System.err
					.println("Completable future interrupted or failed");
				}
				return -1L;
			});
			runs.add((Future<Long>) pool.submit(task));
		}
	}

}

package com.iooota.tests.tss;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TSSTestFixed
{
	public TSSTestFixed()
	{
	}

	public static void main(String[] args) throws InterruptedException, ExecutionException
	{
		int iters = 30;
		if (args.length > 0)
			try
			{
				iters = Integer.parseInt(args[0]);
			}
			catch (NumberFormatException e)
			{
				System.err.println("Could not parse iterations number, defaulting to " + iters);
			}
		int maxThreads = 8;
		if (args.length > 1)
			try
			{
				maxThreads = Integer.parseInt(args[1]);
			}
			catch (NumberFormatException e)
			{
				System.err.println("Could not parse max threads number, defaulting to " + maxThreads);
			}
		int coreThreads = 0;
		if (args.length > 2)
			try
			{
				coreThreads = Integer.parseInt(args[2]);
			}
			catch (NumberFormatException e)
			{
				System.err.println("Could not parse core threads number, defaulting to " + maxThreads);
			}

		System.out.println("Using thread pool with " + coreThreads + " core threads, " + maxThreads
		+ " max threads and idle thread timeout in 5s");
		// final ExecutorService pool = new ThreadPoolExecutor(coreThreads,
		// maxThreads, 5, TimeUnit.SECONDS,
		// new ArrayBlockingQueue<>(20, true));
		final ExecutorService pool = Executors.newWorkStealingPool();

		int thNum = 500;
		System.out.println("Running " + iters + "iterations, " + thNum + " futures each");
		List<Future<Long>> runs = new ArrayList<Future<Long>>(thNum);

		int periodSec = 2;
		for (int i = 0; i < iters; i++)
		{
			buildThreads(thNum, runs, pool);
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

	private static void buildThreads(int thNum, List<Future<Long>> runs, ExecutorService pool)
	{
		runs.clear();
		for (int i = 0; i < thNum; i++)
		{
			runs.add(pool.submit(() ->
			{
				try
				{
					long id = Thread.currentThread().getId();
					TimeUnit.HOURS.sleep(1);
					return id;
				}
				catch (Exception e)
				{
					System.err.println("Completable future interrupted or failed");
				}
				return -1L;
			}));
		}
	}

}

package com.iooota.tests.tss;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TSSTestCf
{
	public TSSTestCf()
	{
	}

	public static void main(String[] args) throws InterruptedException, ExecutionException
	{
		int thNum = 500;
		if (args.length > 0)
			thNum = Integer.parseInt(args[0]);
		System.out.println("Starting test with " + thNum + " threads");
		List<CompletableFuture<Long>> runs = new ArrayList<CompletableFuture<Long>>(thNum);
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

	private static void consumeThreads(List<CompletableFuture<Long>> runs)
	throws InterruptedException, ExecutionException
	{
		List<Long> results = new ArrayList<>(runs.size());
		for (CompletableFuture<Long> r : runs)
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

	private static void buildThreads(int thNum, List<CompletableFuture<Long>> runs)
	{
		runs.clear();
		for (int i = 0; i < thNum; i++)
		{
			runs.add(CompletableFuture.supplyAsync(() ->
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

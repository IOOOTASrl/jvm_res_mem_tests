package com.iooota.tests.tss;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iooota.util.basic.logging.ExceptionUtils;
import com.iooota.util.threads.FutureHelper;
import com.iooota.util.threads.IoootaThreadPoolManager;
import com.iooota.util.threads.IoootaThreadPoolManager.ExecutorStateSnapshot;

public class IoootaThreadPoolsTest
{

	private static final Logger log = LoggerFactory.getLogger(IoootaThreadPoolsTest.class);
	public IoootaThreadPoolsTest()
	{
	}

	public static void main(String[] args) throws InterruptedException, ExecutionException
	{
		// execShutdownTest();
		// dynamicPoolSizingTest();
		// standardExecutorTest();
		// priorityExecutorTest();
		// looseExecutorTest();
		threadsNameTest();
		// executorReuseTest();
		// safeFutureTaskTest();
		// waitAndRealeaseTest();
		// subtaskCollectorTest();
		
	}

	protected static void execShutdownTest() throws InterruptedException
	{
		ScheduledExecutorService scheduler = IoootaThreadPoolManager.scheduler("shutdownTest");
		Callable<Integer> callable = () ->
		{
			log.info("Future task running");
			return 0;
		};
		scheduler.schedule(FutureHelper.safeFutureTask(callable), 1, TimeUnit.SECONDS);
		TimeUnit.SECONDS.sleep(3);
		scheduler.shutdown();
		scheduler.schedule(FutureHelper.safeFutureTask(callable), 1, TimeUnit.SECONDS);
	}

	protected static void subtaskCollectorTest() throws InterruptedException, ExecutionException
	{
		Callable<Date> callable = () ->
		{
			Date date = new Date();
			log.info("Callable {} completed at time {}", Thread.currentThread().getName(), date);
			return date;
		};

		ExecutorService executor = IoootaThreadPoolManager.looseTasksExecutor("subtaskTest", 6);

		List<FutureTask<Date>> tasks = new ArrayList<>();

		for (int i = 0; i < 5; i++)
			tasks.add(FutureHelper.safeFutureTask(callable));

		FutureTask<List<Date>> futureDates = FutureHelper.safeFutureTask(() ->
		{
			List<Date> ds = new ArrayList<>();
			log.info("Launching subtasks");
			for (FutureTask<Date> t : tasks)
			{
				executor.submit(t);
				TimeUnit.SECONDS.sleep(2);
			}
			log.info("Retrieving subtasks' values");
			for (FutureTask<Date> t : tasks)
				ds.add(t.get());
			return ds;
		});

		log.info("Launching the main container task");
		executor.submit(futureDates);
		List<Date> dates = futureDates.get();
		log.info("Main task finished");
		dates.forEach(d -> log.info("Date retrieved: {}", d));
	}

	protected static void waitAndRealeaseTest() throws InterruptedException, ExecutionException
	{
		Object obj = new Object();
		Callable<Void> callable = () ->
		{
			try
			{
				synchronized (obj)
				{
					log.info("Thread locking in wait...");
					obj.wait();
					log.info("Waiting finished");
				}
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			return null;
		};

		FutureTask<Void> safeFutureTask = FutureHelper.safeFutureTask(callable);

		ExecutorService exec = IoootaThreadPoolManager.looseTasksExecutor("safeTaskTest", 1);
		exec.submit(safeFutureTask);
		TimeUnit.SECONDS.sleep(5);
		log.info("Releasing the waiting thread");
		synchronized (obj)
		{
			obj.notifyAll();
		}
		safeFutureTask.get();
		log.info("All went good");
	}

	protected static void safeFutureTaskTest() throws InterruptedException, ExecutionException
	{
		Object obj = new Object();
		Callable<Void> callable = () ->
		{
			try
			{
				synchronized (obj)
				{
					obj.wait();
				}
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			return null;
		};

		FutureTask<Void> safeFutureTask = FutureHelper.safeFutureTask(Duration.ofSeconds(3), callable);

		log.info("First stalling task will be canceled because of a 3s maximum timeout given in construction");
		ExecutorService exec = IoootaThreadPoolManager.looseTasksExecutor("safeTaskTest", 1);
		exec.submit(safeFutureTask);
		safeFutureTask.get();

		safeFutureTask = FutureHelper.safeFutureTask(callable);
		exec.submit(safeFutureTask);
		try
		{
			safeFutureTask.get(5, TimeUnit.SECONDS);
		}
		catch (Exception e)
		{
			ExceptionUtils.logError(log, e, "Second task canceled because of get with timeout invocation: {}",
			e.toString());
		}

		TimeUnit.SECONDS.sleep(20);
	}

	protected static void executorReuseTest() throws InterruptedException
	{
		Runnable r = () ->
		{
			Thread currentThread = Thread.currentThread();
			System.out.println("Simple executor: name = " + currentThread.getName() + " - group = "
			+ currentThread.getThreadGroup().getName());
			try
			{
				TimeUnit.SECONDS.sleep(1);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		};

		ExecutorService fooExec = IoootaThreadPoolManager.looseTasksExecutor("foo");
		ExecutorService fooExec2 = IoootaThreadPoolManager.looseTasksExecutor("foo");
		fooExec.submit(r);
		fooExec2.submit(r);
		System.out
		.println("Checking retrieval of the same loose executor: " + (fooExec == fooExec2) + " (" + fooExec + ")");
		System.out.println(IoootaThreadPoolManager.getExecutorsStateAsStr());

		fooExec = IoootaThreadPoolManager.looseTasksExecutor("foo", 2);
		fooExec2 = IoootaThreadPoolManager.looseTasksExecutor("foo", 2);
		fooExec.submit(r);
		fooExec2.submit(r);
		System.out
		.println("Checking retrieval of the same loose executor with custom pool size: " + (fooExec == fooExec2) + " ("
		+ fooExec + ")");
		System.out.println(IoootaThreadPoolManager.getExecutorsStateAsStr());

		ExecutorService barExec = IoootaThreadPoolManager.priorityTasksExecutor("bar");
		ExecutorService barExec2 = IoootaThreadPoolManager.priorityTasksExecutor("bar");
		barExec.submit(r);
		barExec2.submit(r);
		System.out
		.println("Checking retrieval of the same priority executor: " + (barExec == barExec2) + " (" + barExec + ")");
		System.out.println(IoootaThreadPoolManager.getExecutorsStateAsStr());

		barExec = IoootaThreadPoolManager.priorityTasksExecutor("bar", 3);
		barExec2 = IoootaThreadPoolManager.priorityTasksExecutor("bar", 3);
		barExec.submit(r);
		barExec2.submit(r);
		System.out.println("Checking retrieval of the same priority executor with custom pool size: "
		+ (barExec == barExec2) + " (" + barExec + ")");
		System.out.println(IoootaThreadPoolManager.getExecutorsStateAsStr());

		ScheduledExecutorService bazSched = IoootaThreadPoolManager.scheduler("baz");
		ScheduledExecutorService bazSched2 = IoootaThreadPoolManager.scheduler("baz");
		bazSched.schedule(r, 3, TimeUnit.SECONDS);
		bazSched2.schedule(r, 3, TimeUnit.SECONDS);
		System.out
		.println("Checking retrieval of the same scheduler: " + (bazSched == bazSched2) + " (" + bazSched + ")");
		System.out.println(IoootaThreadPoolManager.getExecutorsStateAsStr());

		bazSched = IoootaThreadPoolManager.scheduler("baz", 4);
		bazSched2 = IoootaThreadPoolManager.scheduler("baz", 4);
		bazSched.schedule(r, 3, TimeUnit.SECONDS);
		bazSched2.schedule(r, 3, TimeUnit.SECONDS);
		System.out.println("Checking retrieval of the same scheduler with custom pool size: " + (bazSched == bazSched2)
		+ " (" + bazSched + ")");

		Map<String, ExecutorStateSnapshot> executorsState = IoootaThreadPoolManager.getExecutorsState();
		executorsState.forEach((id, snap) ->
		{
			System.out.println("State for " + id + ": " + snap + "; type = " + snap.type);
		});

		TimeUnit.SECONDS.sleep(20);
	}

	@Deprecated
	protected static void dynamicPoolSizingTest() throws InterruptedException
	{
		Runnable r = () ->
		{
			Thread currentThread = Thread.currentThread();
			System.out.println("Simple executor: name = " + currentThread.getName() + " - group = "
			+ currentThread.getThreadGroup().getName());
			try
			{
				TimeUnit.MINUTES.sleep(1);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		};
		ExecutorService exec = IoootaThreadPoolManager.executor("default");
		System.out.println("Executor has max pool size: " + ((ThreadPoolExecutor) exec).getMaximumPoolSize());
		System.out.println("Executor has core pool size: " + ((ThreadPoolExecutor) exec).getCorePoolSize());

		for (int i = 0; i < 50 * 10; i++)
		{
			exec.submit(r);
			System.out.println("Queue size is now " + ((ThreadPoolExecutor) exec).getQueue().size()
			+ ", remaining capacity " + ((ThreadPoolExecutor) exec).getQueue().remainingCapacity());
		}
		TimeUnit.HOURS.sleep(1);
	}

	protected static void standardExecutorTest() throws InterruptedException
	{
		Runnable r = () ->
		{
			Thread currentThread = Thread.currentThread();
			System.out.println("Simple executor: name = " + currentThread.getName() + " - group = "
			+ currentThread.getThreadGroup().getName());
			try
			{
				TimeUnit.MINUTES.sleep(1);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		};

		ExecutorService ex = IoootaThreadPoolManager.executor;

		for (int i = 0; i < 50 * 10; i++)
		{
			ex.submit(r);
		}

		TimeUnit.HOURS.sleep(1);
	}

	protected static void priorityExecutorTest() throws InterruptedException
	{

		ExecutorService pri = IoootaThreadPoolManager.priorityTasksExecutor("primo");
		System.out.println("Executor primo has max pool size: " + ((ThreadPoolExecutor) pri).getMaximumPoolSize());
		ExecutorService sec = IoootaThreadPoolManager.priorityTasksExecutor("secondo");
		ExecutorService ter = IoootaThreadPoolManager.priorityTasksExecutor("terzo");
		ExecutorService qua = IoootaThreadPoolManager.priorityTasksExecutor("quarto");
		ExecutorService qui = IoootaThreadPoolManager.priorityTasksExecutor("quinto");
		ExecutorService ses = IoootaThreadPoolManager.priorityTasksExecutor("sesto");
		ExecutorService set = IoootaThreadPoolManager.priorityTasksExecutor("settimo");
		ExecutorService ott = IoootaThreadPoolManager.priorityTasksExecutor("ottavo");
		ExecutorService non = IoootaThreadPoolManager.priorityTasksExecutor("nono");
		ExecutorService dec = IoootaThreadPoolManager.priorityTasksExecutor("decimo");

		final AtomicInteger doneCount = new AtomicInteger(0);
		Runnable r = () ->
		{
			Thread currentThread = Thread.currentThread();
			try
			{
				TimeUnit.SECONDS.sleep(5);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			System.out.println("Thread name = " + currentThread.getName() + " - group = "
			+ currentThread.getThreadGroup().getName() + " completed: " + doneCount.incrementAndGet() + " done");

			System.out.println(IoootaThreadPoolManager.getExecutorsStateAsStr());
		};

		System.out.println("Delivering tasks to the executors");
		for (int i = 0; i < 50; i++)
		{
			pri.submit(r);
			sec.submit(r);
			ter.submit(r);
			qua.submit(r);
			qui.submit(r);
			ses.submit(r);
			set.submit(r);
			ott.submit(r);
			non.submit(r);
			dec.submit(r);
		}
		System.out.println("Delivering finished: all tasks enqueued");


		TimeUnit.HOURS.sleep(1);
	}

	protected static void looseExecutorTest() throws InterruptedException
	{

		ExecutorService pri = IoootaThreadPoolManager.looseTasksExecutor("primo");
		System.out.println("Executor primo has max pool size: " + ((ThreadPoolExecutor) pri).getMaximumPoolSize());
		ExecutorService sec = IoootaThreadPoolManager.looseTasksExecutor("secondo");
		ExecutorService ter = IoootaThreadPoolManager.looseTasksExecutor("terzo");
		ExecutorService qua = IoootaThreadPoolManager.looseTasksExecutor("quarto");
		ExecutorService qui = IoootaThreadPoolManager.looseTasksExecutor("quinto");
		ExecutorService ses = IoootaThreadPoolManager.looseTasksExecutor("sesto");
		ExecutorService set = IoootaThreadPoolManager.looseTasksExecutor("settimo");
		ExecutorService ott = IoootaThreadPoolManager.looseTasksExecutor("ottavo");
		ExecutorService non = IoootaThreadPoolManager.looseTasksExecutor("nono");
		ExecutorService dec = IoootaThreadPoolManager.looseTasksExecutor("decimo");

		final AtomicInteger doneCount = new AtomicInteger(0);
		Runnable r = () ->
		{
			Thread currentThread = Thread.currentThread();
			try
			{
				TimeUnit.SECONDS.sleep(5);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			System.out.println("Thread name = " + currentThread.getName() + " - group = "
			+ currentThread.getThreadGroup().getName() + " completed: " + doneCount.incrementAndGet() + " done");

			System.out.println(IoootaThreadPoolManager.getExecutorsStateAsStr());
		};

		System.out.println("Delivering tasks to the executors");
		for (int i = 0; i < 50; i++)
		{
			pri.submit(r);
			sec.submit(r);
			ter.submit(r);
			qua.submit(r);
			qui.submit(r);
			ses.submit(r);
			set.submit(r);
			ott.submit(r);
			non.submit(r);
			dec.submit(r);
		}
		System.out.println("Delivering finished: all tasks enqueued");

		TimeUnit.HOURS.sleep(1);
	}

	private static long completed(ExecutorService exec)
	{
		return ((ThreadPoolExecutor) exec).getCompletedTaskCount();
	}

	protected static void threadsNameTest() throws InterruptedException
	{
		Callable<Void> r = () ->
		{
			Thread currentThread = Thread.currentThread();
			System.out.println("Simple executor: name = " + currentThread.getName() + " - group = "
			+ currentThread.getThreadGroup().getName());
			try
			{
				TimeUnit.SECONDS.sleep(1);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			return null;
		};

		// IoootaThreadPoolManager.executor.submit(FutureHelper.safeFutureTask(r));
		// IoootaThreadPoolManager.executor.submit(FutureHelper.safeFutureTask(r));
		//
		// IoootaThreadPoolManager.scheduler.schedule(FutureHelper.safeFutureTask(r),
		// 3, TimeUnit.SECONDS);
		// IoootaThreadPoolManager.scheduler.schedule(FutureHelper.safeFutureTask(r),
		// 3, TimeUnit.SECONDS);

		ExecutorService pippoLoose = IoootaThreadPoolManager.looseTasksExecutor("pippo");
		pippoLoose.submit(FutureHelper.safeFutureTask(r));
		pippoLoose.submit(FutureHelper.safeFutureTask(r));

		ExecutorService pippoPrior = IoootaThreadPoolManager.priorityTasksExecutor("pippo");
		pippoPrior.submit(FutureHelper.safeFutureTask(r));
		pippoPrior.submit(FutureHelper.safeFutureTask(r));

		ScheduledExecutorService plutoSched = IoootaThreadPoolManager.scheduler("pluto");
		plutoSched.schedule(FutureHelper.safeFutureTask(r), 3, TimeUnit.SECONDS);
		plutoSched.schedule(FutureHelper.safeFutureTask(r), 3, TimeUnit.SECONDS);
		plutoSched.scheduleAtFixedRate(FutureHelper.safeFutureTask(r), 3, 3, TimeUnit.SECONDS);

		TimeUnit.SECONDS.sleep(120);
	}

}

/*
 *
 * Copyright (c) 1997-1999 Scott Oaks and Henry Wong. All Rights Reserved.
 *
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for NON-COMMERCIAL purposes and
 * without fee is hereby granted.
 *
 * This sample source code is provided for example only,
 * on an unsupported, as-is basis.
 *
 * AUTHOR MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. AUTHOR SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 *
 * THIS SOFTWARE IS NOT DESIGNED OR INTENDED FOR USE OR RESALE AS ON-LINE
 * CONTROL EQUIPMENT IN HAZARDOUS ENVIRONMENTS REQUIRING FAIL-SAFE
 * PERFORMANCE, SUCH AS IN THE OPERATION OF NUCLEAR FACILITIES, AIRCRAFT
 * NAVIGATION OR COMMUNICATION SYSTEMS, AIR TRAFFIC CONTROL, DIRECT LIFE
 * SUPPORT MACHINES, OR WEAPONS SYSTEMS, IN WHICH THE FAILURE OF THE
 * SOFTWARE COULD LEAD DIRECTLY TO DEATH, PERSONAL INJURY, OR SEVERE
 * PHYSICAL OR ENVIRONMENTAL DAMAGE ("HIGH RISK ACTIVITIES").  AUTHOR
 * SPECIFICALLY DISCLAIMS ANY EXPRESS OR IMPLIED WARRANTY OF FITNESS FOR
 * HIGH RISK ACTIVITIES.
 */
package myproxy.threadpool;

import java.util.Vector;

public final class ThreadPool
{

	private final class ThreadPoolRequest
	{
		Runnable target;
		Object lock;

		ThreadPoolRequest(Runnable t, Object l)
		{
			target = t;
			lock = l;
		}
	}

	private final class ThreadPoolThread extends Thread
	{
		ThreadPool parent;
		boolean shouldRun = true;

		ThreadPoolThread(ThreadPool parent, int i, String name)
		{
			super(name + " - ThreadPoolThread " + i);
			this.parent = parent;
		}

		public void run()
		{
			ThreadPoolRequest obj = null;
			while (shouldRun)
			{
				try
				{
					parent.cvFlag.getBusyFlag();
					while (obj == null && shouldRun)
					{
						try
						{
							obj = (ThreadPoolRequest)
								parent.objects.elementAt(0);
							parent.objects.removeElementAt(0);
						}
						catch (ArrayIndexOutOfBoundsException aiobe)
						{
							obj = null;
						}
						catch (ClassCastException cce)
						{
							obj = null;
						}
						if (obj == null)
						{
							try
							{
								parent.cvAvailable.cvWait();
							}
							catch (InterruptedException ie)
							{
								return;
							}
						}
					}
				}
				finally
				{
					parent.cvFlag.freeBusyFlag();
				}
				if (!shouldRun)
				{
					return;
				}
				obj.target.run();
				try
				{
					parent.cvFlag.getBusyFlag();
					nObjects--;
					if (nObjects < nMaxThreads)
					{
						parent.cvEmpty.cvSignal();
					}
				}
				finally
				{
					parent.cvFlag.freeBusyFlag();
				}
				if (obj.lock != null)
				{
					synchronized (obj.lock)
					{
						obj.lock.notify();
					}
				}
				obj = null;
			}
		}
	}

	private Vector objects;
	private int nObjects = 0;
	private int nMaxThreads = 0;
	private CondVar cvAvailable, cvEmpty;
	private BusyFlag cvFlag;
	private ThreadPoolThread poolThreads[];
	private boolean terminated = false;

	public ThreadPool(String name, int m_maxConcurrentThreads)
	{
		this(name,m_maxConcurrentThreads,Thread.NORM_PRIORITY);
	}

	public ThreadPool(String name, int n,int priority)
		{
		cvFlag = new BusyFlag();
		cvAvailable = new CondVar(cvFlag);
		cvEmpty = new CondVar(cvFlag);
		objects = new Vector();
		nMaxThreads = n;
		poolThreads = new ThreadPoolThread[n];
		if (name == null)
		{
			name = "";
		}
		for (int i = 0; i < n; i++)
		{
			poolThreads[i] = new ThreadPoolThread(this, i, name);
			poolThreads[i].setPriority(priority);
			poolThreads[i].setDaemon(true);
			poolThreads[i].start();
		}
	}

	private void add(Runnable target, Object lock)
	{
		try
		{
			cvFlag.getBusyFlag();
			if (terminated)
			{
				throw new IllegalStateException("Thread pool has shutdown");
			}
			objects.addElement(new ThreadPoolRequest(target, lock));
			nObjects++;
			cvAvailable.cvSignal();
			while (nObjects > nMaxThreads)
			{
				try
				{
					cvEmpty.cvWait();
				}
				catch (InterruptedException ie)
				{}
			}
		}
		finally
		{
			cvFlag.freeBusyFlag();
		}
	}

	public void addRequest(Runnable target)
	{
		add(target, null);
	}

	public void addRequestAndWait(Runnable target) throws InterruptedException
	{
		Object lock = new Object();
		synchronized (lock)
		{
			add(target, lock);
			lock.wait();
		}
	}

	/*public void waitForAll(boolean terminate) throws InterruptedException {
	 try {
	  cvFlag.getBusyFlag();
	  while (nObjects != 0)
	   cvEmpty.cvWait();
	  if (terminate) {
	   for (int i = 0; i < poolThreads.length; i++)
	 poolThreads[i].shouldRun = false;
	   cvAvailable.cvBroadcast();
	   terminated = true;
	  }
	 } finally {
	  cvFlag.freeBusyFlag();
	 }
	  }

	  public void waitForAll() throws InterruptedException {
	 waitForAll(false);
	  }*/
}

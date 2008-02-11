/**
 * This file is part of MyProxy.
 * 
 * Copyright (C) 2002 Alexander Dietrich
 * 
 * MyProxy is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * MyProxy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MyProxy; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package myproxy;

import java.util.*;
import java.util.logging.*;

/**
 * Executes tasks at or slightly after
 * their scheduled time, in minute intervals.
 */
public final class Scheduler implements Runnable
{
	public interface Task
	{
		public void execute(Scheduler scheduler);
	}

	private static final Logger _logger = Logger.getLogger("myproxy");
	private final List _tasks;
	private boolean _halt;
	
	public Scheduler()
	{
		_tasks = new Vector();
		_halt = false;
	}
	
	public void run()
	{
		try
		{
			while(true)
			{
				if(isHalted())
					return;
				Thread.sleep(60 * 1000);
				if(isHalted())
					return;
				executeTasks();
			}
		}
		catch(InterruptedException e)
		{
			_logger.logp(Level.WARNING, "Scheduler", "run", "Scheduler interrupted, exiting.", e);
		}
	}
	
	public void queue(Task task, long time)
	{
		synchronized(_tasks)
		{
			_tasks.add(new Object[] { task, new Long(time) });
		}
	}
	
	public synchronized void halt()
	{
		_halt = true;
	}
	
	private synchronized boolean isHalted()
	{
		return _halt;
	}
	
	private void executeTasks()
	{
		List execute = new Vector();
		
		synchronized(_tasks)
		{
			for(Iterator i = _tasks.iterator(); i.hasNext();)
			{
				Object[] pair = (Object[])i.next();
				Task task = (Task)pair[0];
				long time = ((Long)pair[1]).longValue();
				if(time <= System.currentTimeMillis())
				{
					i.remove();
					execute.add(task);
				}
			}
		}
		
		for(Iterator i = execute.iterator(); i.hasNext();)
		{
			Task task = (Task)i.next();
			task.execute(this);
		}
	}
}

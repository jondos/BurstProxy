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

public final class HandlerPool implements Scheduler.Task
{
	public static final long INTERVAL = 10 * 60 * 1000;
	private static final int MAX_POOLSIZE = 5;
	
	private final List _active, _pool;
	private final MyProxy _controller;
	
	public HandlerPool(MyProxy controller)
	{
		_active = new Vector();
		_pool = new Vector();
		_controller = controller;
	}
	
	public synchronized Handler getHandler()
	{
		Handler handler;
		
		if(_pool.size() > 0)
		{
			handler = (Handler)_pool.get(0);
			_pool.remove(0);
		}
		else
		{
			handler = new Handler(_controller);
		}
		_active.add(handler);
		
		return handler;
	}
	
	public synchronized void returnHandler(Handler handler)
	{
		if(_active.remove(handler))
			_pool.add(handler);
	}
	
	public synchronized int activeCount()
	{
		return _active.size();
	}
	
	public synchronized void execute(Scheduler scheduler)
	{
		while(_pool.size() > MAX_POOLSIZE)
			_pool.remove(0);
		scheduler.queue(this, System.currentTimeMillis() + INTERVAL);
	}
}

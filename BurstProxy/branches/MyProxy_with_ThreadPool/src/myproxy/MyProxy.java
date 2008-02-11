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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

import myproxy.threadpool.ThreadPool;



/**
 * The main controller. Listens for client connection,
 * creates the handlers and maintains the user settings
 * and local resources.
 */
public final class MyProxy implements Runnable
{
	private static final Logger _logger = Logger.getLogger("myproxy");
	private static final long REFRESH_INTERVAL = 60 * 60 * 1000;
	
	private final InetSocketAddress _localAddress, _forwardAddress;
	public final ThreadPool _threadPool;
	private final HandlerPool _handlerPool;
	private final File _configDir;
	private final Map _userSettings, _resources;
	private final Scheduler _scheduler;
	private final ArrayList _supportedPrefetchStrategies;
	private final InetSocketAddress _prefetchRemoteEndAddress;

	private ServerSocket _socket;
	private boolean _keepRunning;
	
	public MyProxy()
	{
		if(System.getProperty("myproxy.dir") == null)
		{
			System.err.println("System property myproxy.dir undefined, aborting.");
			System.exit(-1);
		}
		
		_configDir = new File(System.getProperty("myproxy.dir"));
		if(!_configDir.isDirectory() || !_configDir.canWrite())
		{
			System.err.println("Cannot access configuration directory, aborting.");
			System.exit(-1);
		}
		
		String hostname;
		int port = -1;
		
		try
		{
			port = Integer.parseInt(System.getProperty("myproxy.local.port", "8080"));
		}
		catch(NumberFormatException e)
		{
			System.err.println("Invalid port number, aborting.");
			System.exit(-1);
		}
		
		hostname = System.getProperty("myproxy.local.hostname", "localhost");

		_localAddress = new InetSocketAddress(hostname, port);
		
		// NOTE: This security check makes sense for a local end, but not for a remote end!
		/*if(_localAddress.isUnresolved() || _localAddress.getAddress().isAnyLocalAddress())
		{
			System.err.println("Invalid local address, aborting.");
			System.exit(-1);
		}*/

		hostname = System.getProperty("myproxy.forward.host");
		if(hostname != null)
		{
			try
			{
				port = Integer.parseInt(System.getProperty("myproxy.forward.port"));
			}
			catch(NumberFormatException e)
			{
				System.err.println("Invalid forward port number, aborting.");
				System.exit(-1);
			}
			
			_forwardAddress = new InetSocketAddress(hostname, port);
			if(_forwardAddress.equals(_localAddress))
			{
				System.err.println("Invalid forward address, aborting.");
				System.exit(-1);
			}
		}
		else
		{
			_forwardAddress = null;
		}
		
		try
		{
			_socket = new ServerSocket();			
			_socket.bind(_localAddress);
			_socket.setReuseAddress(true);
			_socket.setSoTimeout(1000);
		}
		catch(IOException e)
		{
			System.err.println("Cannot create server socket, aborting. (" + e.getMessage() + ")");
			System.exit(-1);
		}
		
		try
		{
			LogManager.getLogManager().readConfiguration(
				new FileInputStream(new File(_configDir, "logging.properties")));
		}
		catch(IOException e)
		{
			System.err.println("Error reading logging properties, aborting.");
			System.exit(-1);
		}
		_keepRunning = true;
		_handlerPool = new HandlerPool(this);
		_userSettings = new HashMap();
		_userSettings.put("default", new UserSettings(_configDir, "default"));
		_resources = new HashMap();
		_resources.put("default.css", readFile(new File(_configDir, "default.css")));
		_resources.put("blank.gif", readFile(new File(_configDir, "blank.gif")));
		_threadPool = new ThreadPool("Worker", 
				getSettings("default").getInteger("threadpool.size", UserSettings.DEFAULT_THREADPOOL_SIZE));
		_scheduler = new Scheduler();
		_scheduler.queue(_handlerPool, System.currentTimeMillis() + HandlerPool.INTERVAL);
		_scheduler.queue(new Cleaner(), System.currentTimeMillis() + Cleaner.INTERVAL);
		_scheduler.queue(new Scheduler.Task() {
			public void execute(Scheduler scheduler)
			{
				refreshAllRules();
				writeAllRules();
				scheduler.queue(this, System.currentTimeMillis() + REFRESH_INTERVAL);
			}
		}, System.currentTimeMillis() + REFRESH_INTERVAL);
		Thread scheduler = new Thread(_scheduler, "Scheduler");
		scheduler.setDaemon(true);
		scheduler.start();
				
		// specify available prefetching strategies; only a single one for now
		_supportedPrefetchStrategies = new ArrayList();
		_supportedPrefetchStrategies.add("toptobottom");
		
		String prefetchRemoteEndAddress = getSettings("default").get("prefetching.remoteaddress",
				UserSettings.PREFETCHING_DEFAULT_REMOTE_ADDRESS);
		int indexOfColon = prefetchRemoteEndAddress.indexOf(':');
		
		if(getSettings("default").getInteger("prefetching.value",
				UserSettings.PREFETCHING_DISABLED) == UserSettings.PREFETCHING_LOCALEND &&
				indexOfColon != -1) {
			
			hostname = prefetchRemoteEndAddress.substring(0,indexOfColon);
			if(hostname != null)
			{
				try
				{
					port = Integer.parseInt(prefetchRemoteEndAddress.substring(indexOfColon+1));
				}
				catch(NumberFormatException e)
				{
					System.err.println("Invalid prefetching remote end port number, aborting.");
					System.exit(-1);
				}
				
				_prefetchRemoteEndAddress = new InetSocketAddress(hostname, port);
				if(_prefetchRemoteEndAddress.equals(_localAddress))
				{
					System.err.println("Invalid prefetching remote end address, aborting.");
					System.exit(-1);
				}
			} else {
				_prefetchRemoteEndAddress = null;
			}
		}
		else
		{
			_prefetchRemoteEndAddress = null;	
		}
	}
	
	InetSocketAddress getLocalAddress()
	{
		return _localAddress;
	}
	
	InetSocketAddress getForwardAddress()
	{
		return _forwardAddress;
	}
	
	InetSocketAddress getPrefetchingRemoteAddress()
	{
		return _prefetchRemoteEndAddress;
	}

	UserSettings getSettings(String userID)
	{
		return (UserSettings)_userSettings.get(userID);
	}
	
	byte[] getResource(String name)
	{
		return (byte[])_resources.get(name);
	}
	
	void handlerFinished(Handler handler)
	{
		_handlerPool.returnHandler(handler);
	}
	
	synchronized void requestShutdown()
	{
		_keepRunning = false;
	}
	
	private synchronized boolean keepRunning()
	{
		return _keepRunning;
	}
	
	private void refreshAllRules()
	{
		for(Iterator i = _userSettings.values().iterator(); i.hasNext();)
			((UserSettings)i.next()).refreshAllRules();
	}
	
	private void writeAllRules()
	{
		for(Iterator i = _userSettings.values().iterator(); i.hasNext();)
			((UserSettings)i.next()).writeAllRules();
	}

	private byte[] readFile(File file)
	{
		if(file.exists() && file.canRead())
		{
			try
			{
				FileInputStream in = new FileInputStream(file);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				int read = 0;
				
				while((read = in.read(buffer)) != -1)
					out.write(buffer, 0, read);
				
				in.close();
				return out.toByteArray();
			}
			catch(IOException e)
			{
				_logger.logp(Level.CONFIG, "MyProxy", "readFile", "Error reading file: " + file, e);
			}
		}
		
		return null;
	}
	
	public void run()
	{
		try
		{
			while(keepRunning())
			{
				try
				{
					Socket clientSocket = _socket.accept();
					_handlerPool.getHandler().handleClient(clientSocket);
				}
				catch(SocketTimeoutException e)
				{
					// ignore
				}
			}
			
			_scheduler.halt();
			_socket.close();
			
			while(_handlerPool.activeCount() > 0)
				Thread.yield();
				
			writeAllRules();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void work(Runnable target){
		_threadPool.addRequest(target);
	}

	public static void main(String[] args)
	{
		new Thread(new MyProxy(), "MyProxy").start();
	}

	public final ArrayList getSupportedPrefetchStrategies() {
		return _supportedPrefetchStrategies;
	}
}

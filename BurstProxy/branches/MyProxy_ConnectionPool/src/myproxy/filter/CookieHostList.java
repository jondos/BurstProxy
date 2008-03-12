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
package myproxy.filter;

import java.io.*;
import java.util.*;

public final class CookieHostList
{
	private final List _hosts;
	private int _nextID;
	
	public CookieHostList()
	{
		_hosts = new Vector();
		_nextID = 0;
	}
	
	public synchronized void add(String host, boolean exactMatch)
	{
		_hosts.add(new CookieHost(nextID(), host, 0, exactMatch));
		sortByHitCount();
	}
	
	public synchronized boolean remove(int id)
	{
		CookieHost myHost = find(id);
		if(myHost != null && _hosts.remove(myHost))
			return true;
		else
			return false;
	}
	
	
	public synchronized Iterator sortedIterator()
	{
		Object[] array = _hosts.toArray();
		
		Arrays.sort(array, new HostRuleComparator(false));
		return Arrays.asList(array).iterator();
	}

	/**
	 * Returns <tt>true</tt> if the host list had
	 * to be re-sorted due to hit count order.
	 */
	public synchronized boolean refreshOrder()
	{
		int lastCount = Integer.MAX_VALUE;
		
		for(Iterator i = _hosts.iterator(); i.hasNext();)
		{
			CookieHost host = (CookieHost)i.next();
			if(host.getHitCount() > lastCount)
			{
				sortByHitCount();
				return true;
			}
			lastCount = host.getHitCount();
		}
		
		return false;
	}
	
	/**
	 * @param domain the host/domain string to match
	 * @param incoming <tt>true</tt> if a cookie is coming from the server,
	 *                 <tt>false</tt> if it is coming from the client
	 */
	public synchronized CookieHost match(String domain, boolean incoming)
	{
		if(domain.charAt(0) == '.')
			domain = domain.substring(1);
		if(domain.indexOf(':') != -1)
			domain = domain.substring(0, domain.indexOf(':'));
		
		String[] parts = domain.split("\\.");	
		for(Iterator i = _hosts.iterator(); i.hasNext();)
		{
			CookieHost host = (CookieHost)i.next();
			if(host.matches(parts, incoming))
			{
				if(host.getHitCount() == Integer.MAX_VALUE)
					resetHitCounts();
				return host;
			}
		}
		
		return null;
	}
	
	public synchronized void read(InputStream in) throws IOException
	{
		BufferedReader bin = new BufferedReader(new InputStreamReader(in));
		String line;
		
		_hosts.clear();
		while((line = bin.readLine()) != null)
		{
			line = line.trim();
			if(line.length() == 0)
				continue;
			
			String[] parts = line.split(" ");
			if(parts.length != 2)
				continue;
			
			if(parts[0].charAt(0) != '^')
				_hosts.add(new CookieHost(nextID(), parts[0], Integer.parseInt(parts[1]), false));
			else
				_hosts.add(new CookieHost(nextID(), parts[0].substring(1), Integer.parseInt(parts[1]), true));
		}
		
		sortByHitCount();
	}
	
	public synchronized void write(OutputStream out) throws IOException
	{
		BufferedWriter bout = new BufferedWriter(new OutputStreamWriter(out));
		
		for(Iterator i = _hosts.iterator(); i.hasNext();)
		{
			CookieHost host = (CookieHost)i.next();
			StringBuffer buffer = new StringBuffer(host.getSource());
			if(host.isExactMatch())
				buffer.insert(0, '^');
			buffer.append(' ').append(host.getHitCount());
			
			String line = buffer.toString();
			bout.write(line, 0, line.length());
			bout.newLine();
		}
		
		bout.flush();
	}
	
	private void resetHitCounts()
	{
		for(Iterator i = _hosts.iterator(); i.hasNext();)
		{
			CookieHost host = (CookieHost)i.next();
			host.resetHitCount();
		}
	}
	
	private void sortByHitCount()
	{
		Object[] array = _hosts.toArray();
		Arrays.sort(array, new HostRuleComparator(true));
		_hosts.clear();
		for(int i = 0; i < array.length; i++)
			_hosts.add(array[i]);
	}
	
	private CookieHost find(int id)
	{
		for(Iterator i = _hosts.iterator(); i.hasNext();)
		{
			CookieHost host = (CookieHost)i.next();
			if(host.getID() == id)
				return host;
		}
		
		return null;
	}

	private synchronized int nextID()
	{
		return _nextID++;
	}
}

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

/**
 * Manages a set of <code>Rule</code>s. Has functionality for manipulating
 * the list (add, remove, replace rules), finding a matching rule and
 * reading or writing the list via streams.
 */
public final class URLRuleList
{
	private final List _rules;
	private int _nextID;
	
	public URLRuleList()
	{
		_rules = new Vector();
		_nextID = 0;
	}
	
	public synchronized void add(String hostPart, String pathPart, String comment)
	{
		_rules.add(new URLRule(nextID(), hostPart, pathPart, 0, comment));
		sortByHitCount();
	}

	/**
	 * Returns the requested rule,
	 * if it exists, or <tt>null</tt>.
	 */
	public synchronized URLRule get(int id)
	{
		return find(id);
	}
	
	/**
	 * Returns <tt>true</tt> if the requested
	 * rule was actually removed.
	 */
	public synchronized boolean remove(int id)
	{
		URLRule rule = find(id);
		if(rule != null && _rules.remove(rule))
			return true;
		else
			return false;
	}
	
	/**
	 * Returns <tt>true</tt> if the
	 * rule was replaced.
	 */
	public synchronized boolean replace(int id, String hostPart, String pathPart, String comment)
	{
		URLRule oldRule = find(id);
		if(oldRule == null)
			return false;
		
		URLRule newRule = new URLRule(nextID(), hostPart, pathPart, oldRule.getHitCount(), comment);
		int index = _rules.indexOf(oldRule);
		_rules.add(index, newRule);
		_rules.remove(oldRule);
		
		return true;
	}
	

	/**
	 * Returns an <code>Iterator</code> which iterates over the
	 * rule set in host part/path part ascending order.
	 */
	public synchronized Iterator sortedIterator()
	{
		Object[] array = _rules.toArray();
		
		Arrays.sort(array, new URLRuleComparator(false, false));
		return Arrays.asList(array).iterator();
	}
	
	/**
	 * Returns <tt>true</tt> if the ruleset had
	 * to be re-sorted due to hit count order.
	 */
	public synchronized boolean refreshOrder()
	{
		int lastCount = Integer.MAX_VALUE;
		
		for(Iterator i = _rules.iterator(); i.hasNext();)
		{
			URLRule rule = (URLRule)i.next();
			if(rule.getHitCount() > lastCount)
			{
				sortByHitCount();
				return true;
			}
			lastCount = rule.getHitCount();
		}
		
		return false;
	}
	
	/**
	 * Returns a rule that matches both parts, or <tt>null</tt>.
	 */
	public synchronized URLRule match(String hostPart, String pathPart)
	{
		for(Iterator i = _rules.iterator(); i.hasNext();)
		{
			URLRule rule = (URLRule)i.next();
			if(rule.matches(hostPart, pathPart))
			{
				if(rule.getHitCount() == Integer.MAX_VALUE)
					resetHitCounts();
				return rule;
			}
		}
		
		return null;
	}
	
	public synchronized void read(InputStream in) throws IOException
	{
		BufferedReader bin = new BufferedReader(new InputStreamReader(in));
		String line;
		
		_rules.clear();
		while((line = bin.readLine()) != null)
		{
			line = line.trim();
			if(line.length() == 0)
				continue;

			String rule;
			String hostPart = null, pathPart = null, comment = null;
			int hitCount;
			
			int pos = line.indexOf(' ');
			rule = line.substring(0, pos);
			line = line.substring(pos + 1);
			
			pos = rule.indexOf(';');
			if(pos == -1)
			{
				hostPart = rule;
			}
			else
			if(pos == 0)
			{
				pathPart = rule.substring(1);
			}
			else
			{
				hostPart = rule.substring(0, pos);
				pathPart = rule.substring(pos + 1);
			}
			
			if(hostPart != null)
			{
				StringBuffer buffer = new StringBuffer(hostPart);
				while((pos = buffer.indexOf("%20")) != -1)
					buffer.replace(pos, pos + 3, " ");
				hostPart = buffer.toString();
			}
			
			if(pathPart != null)
			{
				StringBuffer buffer = new StringBuffer(pathPart);
				while((pos = buffer.indexOf("%20")) != -1)
					buffer.replace(pos, pos + 3, " ");
				pathPart = buffer.toString();
			}

			pos = line.indexOf(' ');
			if(pos == -1)
			{
				hitCount = Integer.parseInt(line);
			}
			else
			{
				hitCount = Integer.parseInt(line.substring(0, pos));
				comment = line.substring(pos + 1);
			}
			
			_rules.add(new URLRule(nextID(), hostPart, pathPart, hitCount, comment));
		}
		
		sortByHitCount();
	}
	
	public synchronized void write(OutputStream out) throws IOException
	{
		BufferedWriter bout = new BufferedWriter(new OutputStreamWriter(out));
		
		for(Iterator i = _rules.iterator(); i.hasNext();)
		{
			URLRule rule = (URLRule)i.next();
			StringBuffer buffer = new StringBuffer();
			
			if(rule.getHostPart() != null)
			{
				StringBuffer hostPart = new StringBuffer(rule.getHostPart());
				int pos;
				while((pos = hostPart.indexOf(" ")) != -1)
					hostPart.replace(pos, pos + 1, "%20");
				buffer.append(hostPart.toString());
			}
			
			if(rule.getPathPart() != null)
			{
				StringBuffer pathPart = new StringBuffer(rule.getPathPart());
				int pos;
				while((pos = pathPart.indexOf(" ")) != -1)
					pathPart.replace(pos, pos + 1, "%20");
				buffer.append(';').append(pathPart.toString());
			}
			
			buffer.append(' ').append(rule.getHitCount());
			
			if(rule.getComment() != null)
				buffer.append(' ').append(rule.getComment());
			
			String line = buffer.toString();
			bout.write(line, 0, line.length());
			bout.newLine();
		}
		
		bout.flush();
	}

	private void resetHitCounts()
	{
		for(Iterator i = _rules.iterator(); i.hasNext();)
		{
			URLRule rule = (URLRule)i.next();
			rule.resetHitCount();
		}
	}
	
	private void sortByHitCount()
	{
		Object[] array = _rules.toArray();
		Arrays.sort(array, new URLRuleComparator(true, true));
		_rules.clear();
		for(int i = 0; i < array.length; i++)
			_rules.add(array[i]);
	}
	
	private URLRule find(int id)
	{
		for(Iterator i = _rules.iterator(); i.hasNext();)
		{
			URLRule rule = (URLRule)i.next();
			if(rule.getID() == id)
				return rule;
		}
		
		return null;
	}
	
	private synchronized int nextID()
	{
		return _nextID++;
	}
}

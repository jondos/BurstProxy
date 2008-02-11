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

import java.util.Comparator;

/**
 * Compares <code>Rule</code>s by
 * host and path part, or by hit count.
 */
public final class URLRuleComparator implements Comparator
{
	private final boolean _exact;
	private final boolean _byHitCount;

	/**
	 * @param exact <tt>false</tt> if leading '^' and trailing '$' are to be ignored
	 */
	public URLRuleComparator(boolean exact, boolean byHitCount)
	{
		_exact = exact;
		_byHitCount = byHitCount;
	}
	
	public int compare(Object o1, Object o2)
	{
		URLRule a = (URLRule)o1;
		URLRule b = (URLRule)o2;
		
		if(_byHitCount)
		{
			if(a.getHitCount() > b.getHitCount())
				return -1;
			else
				return (a.getHitCount() < b.getHitCount() ? 1 : 0);
		}
		else
		{
			return comparePatterns(a, b);
		}
	}

	private	int comparePatterns(URLRule a, URLRule b)
	{
		String hostA = adjust(a.getHostPart());
		String pathA = adjust(a.getPathPart());
		String hostB = adjust(b.getHostPart());
		String pathB = adjust(b.getPathPart());
		
		int result = 0;
		
		if(hostA != null && hostB != null)
		{
			result = hostA.compareToIgnoreCase(hostB);
		}
		else
		{
			if(hostA == null && hostB == null)
				result = 0;
			else if(hostA == null)
				return 1;
			else
				return -1;
		}
		
		if(result != 0)
			return result;
		
		if(pathA != null && pathB != null)
		{
			result = pathA.compareToIgnoreCase(pathB);
		}
		else
		{
			if(pathA == null && pathB == null)
				result = 0;
			else if(pathA == null)
				return -1;
			else
				return 1;
		}
		
		return result;
	}
	
	private String adjust(String part)
	{
		if(_exact || part == null)
			return part;

		int start, end;			
		if(part.charAt(0) == '^')
			start = 1;
		else
			start = 0;
		if(part.charAt(part.length() - 1) == '$')
			end = part.length() - 1;
		else
			end = part.length();
			
		return part.substring(start, end);
	}
}

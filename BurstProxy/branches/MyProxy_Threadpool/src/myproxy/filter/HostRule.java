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

/**
 * Contains a host or domain name pattern that can be compared
 * component by component against other host or domain names,
 * starting from the right.
 * Wildcards ('*' and '?') can be used and the host names to
 * compare against may be longer or shorter than the pattern.
 */
public class HostRule
{
	private final int _id;
	private final String _source;
	private final Object[] _parts;
	private int _hitCount;
	
	HostRule(int id, String host, int hitCount)
	{
		_id = id;

		if(host.charAt(0) == '.')
			host = host.substring(1);

		if(host.indexOf(':') != -1)
			host = host.substring(0, host.indexOf(':'));

		_source = host;
		String[] strings = _source.split("\\.");
		_parts = new Object[strings.length];
		for(int i = 0; i < _parts.length; i++)
		{
			if(
				strings[i].indexOf('*') == -1 &&
				strings[i].indexOf('?') == -1
			)
				_parts[i] = strings[i];
			else
				_parts[i] = new WildcardMatcher(strings[i]);
		}

		_hitCount = hitCount;
	}
	
	final boolean matches(String[] hostParts, boolean allowLonger, boolean allowShorter)
	{
		if(hostParts.length < _parts.length && !allowShorter)
			return false;
		else if(hostParts.length > _parts.length && !allowLonger)
			return false;
		
		int compareLength = Math.min(hostParts.length, _parts.length);	
		for(int i = 0; i < compareLength; i++)
		{
			String hostPart = hostParts[hostParts.length - 1 - i];
			Object myPart = _parts[_parts.length - 1 - i];
			
			if(myPart instanceof String)
			{
				if(!((String)myPart).equalsIgnoreCase(hostPart))
					return false;
			}
			else
			{
				if(!((WildcardMatcher)myPart).matches(hostPart))
					return false;
			}
		}
		
		_hitCount++;
		
		return true;
	}
	
	public final int getID()
	{
		return _id;
	}

	public final String getSource()
	{
		return _source;
	}

	public final int getHitCount()
	{
		return _hitCount;
	}
	
	final void resetHitCount()
	{
		_hitCount = 0;
	}
}

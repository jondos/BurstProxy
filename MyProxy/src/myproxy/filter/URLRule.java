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

import java.util.regex.*;

public final class URLRule
{
	private final int _id;
	private final Pattern _host, _path;
	private final String _comment;
	private int _hitCount;
	
	URLRule(int id, String hostPart, String pathPart, int hitCount, String comment) throws PatternSyntaxException
	{
		if(hostPart == null && pathPart == null)
			throw new IllegalArgumentException("No rule patterns passed.");
		
		_id = id;
		
		if(hostPart != null)
		{
			// strip port 80 from host part
			int pos = hostPart.indexOf(":80");
			if(pos != -1)
			{
				// delete from a StringBuffer because there
				// might be more characters, as it is a regex
				StringBuffer newHost = new StringBuffer(hostPart);
				newHost.delete(pos, pos + 3);
				hostPart = newHost.toString();
			}
			
			_host = Pattern.compile(hostPart, Pattern.CASE_INSENSITIVE);
		}
		else
		{
			_host = null;
		}
			
		if(pathPart != null)
			_path = Pattern.compile(pathPart);
		else
			_path = null;
		
		_hitCount = hitCount;
		_comment = comment;
	}
	
	boolean matches(String hostPart, String pathPart)
	{
		if(_host != null)
		{
			// strip port 80 from host part
			if(hostPart.endsWith(":80"))
				hostPart = hostPart.substring(0, hostPart.length() - 3);
			
			Matcher m = _host.matcher(hostPart);
			if(!m.find())
				return false;
		}
		
		if(_path != null && pathPart != null)
		{
			Matcher m = _path.matcher(pathPart);
			if(!m.find())
				return false;
		}
		
		_hitCount++;
		
		return true;
	}

	public int getID()
	{
		return _id;
	}
	
	public String getHostPart()
	{
		if(_host != null)
			return _host.pattern();
		else
			return null;
	}
	
	public String getPathPart()
	{
		if(_path != null)
			return _path.pattern();
		else
			return null;
	}
	
	public int getHitCount()
	{
		return _hitCount;
	}
	
	void resetHitCount()
	{
		_hitCount = 0;
	}
	
	public String getComment()
	{
		return _comment;
	}
}

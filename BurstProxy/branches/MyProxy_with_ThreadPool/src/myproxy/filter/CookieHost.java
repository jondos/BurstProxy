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

public final class CookieHost extends HostRule
{
	private final boolean _exactMatch;
	
	CookieHost(int id, String host, int hitCount, boolean exactMatch)
	{
		super(id, host, hitCount);
		
		_exactMatch = exactMatch;
	}
	
	boolean matches(String[] hostParts, boolean incoming)
	{
		if(incoming)
			return matches(hostParts, false, true);
		else
			return matches(hostParts, !_exactMatch, false);
	}
	
	public boolean isExactMatch()
	{
		return _exactMatch;
	}
}

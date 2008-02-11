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

public final class HostRuleComparator implements Comparator
{
	private final boolean _byHitCount;
	
	public HostRuleComparator(boolean byHitCount)
	{
		_byHitCount = byHitCount;
	}
	
	public int compare(Object o1, Object o2)
	{
		HostRule a = (HostRule)o1;
		HostRule b = (HostRule)o2;
		
		if(_byHitCount)
		{
			if(a.getHitCount() > b.getHitCount())
				return -1;
			else
				return (a.getHitCount() < b.getHitCount() ? 1 : 0);
		}
		else
		{
			return a.getSource().compareToIgnoreCase(b.getSource());
		}
	}
}

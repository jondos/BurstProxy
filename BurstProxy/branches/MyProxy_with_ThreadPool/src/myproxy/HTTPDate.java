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

import java.text.*;
import java.util.*;

public final class HTTPDate
{
	private final static SimpleDateFormat _formatter;
	private final static Date _current;
	
	static
	{
		_formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
		_formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		_current = new Date();
	}
	
	public synchronized static String now()
	{
		_current.setTime(System.currentTimeMillis());
		return _formatter.format(_current);
	}
}

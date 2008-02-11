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

public final class HTTPException extends Exception
{
	private final static Map _reasons;
	private final String _statusCode, _resourceKey, _detail;
	private final boolean _fatal;
	
	static
	{
		_reasons = new HashMap();
		_reasons.put("400", "Bad Request");
		_reasons.put("404", "Not Found");
		_reasons.put("411", "Length Required");
		_reasons.put("417", "Expectation Failed");
		_reasons.put("500", "Internal Server Error");
		_reasons.put("501", "Not Implemented");
		_reasons.put("502", "Bad Gateway");
		_reasons.put("504", "Gateway Timeout");
	}
	
	public HTTPException(String statusCode, String resourceKey, String detail, boolean fatal)
	{
		_statusCode = statusCode;
		_resourceKey = resourceKey;
		_detail = detail;
		_fatal = fatal;
	}
	
	public String getStatusCode()
	{
		return _statusCode;
	}
	
	public String getReason()
	{
		return (String)_reasons.get(_statusCode);
	}
	
	public String getResourceKey()
	{
		return _resourceKey;
	}
	
	public String getDetail()
	{
		return _detail;
	}
	
	public boolean isFatal()
	{
		return _fatal;
	}
}

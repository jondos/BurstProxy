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
package myproxy.httpio;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.logging.*;
import java.util.regex.*;

/**
 * Splits a URI into its components using the regular expression
 * contained in RFC 2396. Does not do any verification, because
 * weird numbers and exotic characters in the path and query parts
 * frequently cause Sun's <code>URI</code> and <code>URL</code>
 * parsers to explode.
 */
public final class URIParser
{
	private static final Logger _logger = Logger.getLogger("myproxy.httpio");
	private static final Pattern URI =
		Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");
	private static final Pattern URIPATH =
		Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?(.*)");
	
	private String _source, _pathSource;
	private String _scheme, _user, _host, _path, _query, _fragment;
	private int _port;
	
	public URIParser()
	{
		clear();
	}
	
	public void parse(String uri) throws URIFormatException
	{
		clear();
		
		if(uri == null)
			return;
		
		_source = uri;
		
		Matcher m = URI.matcher(uri);
		if(m.matches())
		{
			_scheme = m.group(2);
			if(_scheme != null)
				_scheme = _scheme.toLowerCase();
			
			String authority = m.group(4);
			if(authority != null)
			{
				int pos, hostPos = 0;
				
				if((pos = authority.indexOf('@')) != -1)
				{
					_user = authority.substring(0, pos);
					hostPos = pos + 1;
				}
				
				if((pos = authority.indexOf(':', hostPos)) == -1)
				{
					_host = authority.substring(hostPos);
				}
				else
				{
					_host = authority.substring(hostPos, pos);
					try
					{
						_port = Integer.parseInt(authority.substring(pos + 1));
					}
					catch(NumberFormatException e)
					{
						throw new URIFormatException("Invalid host port: " + authority.substring(pos + 1), uri);
					}
				}
				
				if(_host != null)
					_host = _host.toLowerCase();
			}
			
			_path = m.group(5);
			if(_path != null && _path.length() == 0)
				_path = null;
			
			_query = m.group(7);
			_fragment = m.group(9);
			
			try
			{
				if(_path != null)
					_path = URLDecoder.decode(_path, "UTF-8");
				if(_query != null)
					_query = URLDecoder.decode(_query, "UTF-8");
				if(_fragment != null)
					_fragment = URLDecoder.decode(_fragment, "UTF-8");
			}
			catch(UnsupportedEncodingException e)
			{
				_logger.logp(Level.SEVERE, "URIParser", "parse", "Standard encoding unsupported.", e);
			}
			catch(IllegalArgumentException e)
			{
				throw new URIFormatException(e.getMessage(), uri);
			}
			
			m = URIPATH.matcher(uri);
			if(m.matches())
				_pathSource = m.group(5);
		}
	}
	
	/**
	 * Returns the unmodified URI string, as passed to <code>parse()</code>.
	 */
	public String getSource()
	{
		return _source;
	}
	
	/**
	 * Returns the unmodified path (and following) portion of the URI string,
	 * as passed to <code>parse()</code>. Also called "abs_path".
	 */
	public String getPathSource()
	{
		return _pathSource;
	}

	/**
	 * Returns the scheme portion of the URI in lower-case form,
	 * or <tt>null</tt>
	 */
	public String getScheme()
	{
		return _scheme;
	}
	
	/**
	 * Returns the unmodified user portion of the URI, or <tt>null</tt>.
	 */
	public String getUser()
	{
		return _user;
	}

	/**
	 * Returns the host portion of the URI in lower-case form,
	 * or <tt>null</tt>.
	 */
	public String getHost()
	{
		return _host;
	}

	/**
	 * Returns the decoded path portion of the URI, or <tt>null</tt>.
	 */
	public String getPath()
	{
		return _path;
	}

	/**
	 * Returns the decoded query portion of the URI, or <tt>null</tt>.
	 */
	public String getQuery()
	{
		return _query;
	}

	/**
	 * Returns the decoded fragment portion of the URI, or <tt>null</tt>.
	 */
	public String getFragment()
	{
		return _fragment;
	}
	
	/**
	 * Returns the port portion of the URI, or <tt>-1</tt>.
	 */
	public int getPort()
	{
		return _port;
	}

	private void clear()
	{
		_source = null;
		_pathSource = null;
		_scheme = null;
		_user = null;
		_host = null;
		_path = null;
		_query = null;
		_fragment = null;
		_port = -1;
	}
}

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

import java.io.*;
import java.util.*;
import java.util.logging.*;

import myproxy.filter.*;

/**
 * Contains all the settings and cookie, block and image rules
 * for a user, as well as functionality to read and write them
 * to disk. Also maintains the lists of recently eaten cookies.
 */
public final class UserSettings extends Settings
{
	private static final Logger _logger = Logger.getLogger("myproxy");
	
	public static final int COOKIE_PASS    = 0;
	public static final int COOKIE_CHECK   = 1;
	public static final int COOKIE_EAT     = 2;
	public static final int COOKIE_DEFAULT = 0;

	public static final int REFERER_PASS    = 0;
	public static final int REFERER_TRIM    = 1;
	public static final int REFERER_EAT     = 2;
	public static final int REFERER_DEFAULT = 1;
	
	public static final int FROM_PASS    = 0;
	public static final int FROM_FAKE    = 1;
	public static final int FROM_EAT     = 2;
	public static final int FROM_DEFAULT = 2;
	
	public static final String FROM_DEFAULTFAKE = "fake@from.foo";
	
	public static final int AGENT_PASS    = 0;
	public static final int AGENT_FAKE    = 1;
	public static final int AGENT_EAT     = 2;
	public static final int AGENT_DEFAULT = 0;
	
	public static final String AGENT_DEFAULTFAKE = "Mozilla/4.0 (compatible; MSIE 5.5; Windows 98)";
	
	public static final int PREFETCHING_DISABLED  = 0;
	public static final int PREFETCHING_LOCALEND  = 1;
	public static final int PREFETCHING_REMOTEEND = 2;
	public static final int PREFETCHING_DEFAULT   = 0;
	
	public static final String PREFETCHING_DEFAULT_REMOTE_ADDRESS = "";
	
	public static final int MAX_JARSIZE = 20;
	
	public static final int DEFAULT_THREADPOOL_SIZE = 15;

	private final File _configDir;
	private final CookieHostList _cookieHosts, _sessionCookies, _cookieExceptions;
	private final URLRuleList _blockRules, _blockExceptions, _imageRules;
	private final List _inJar, _outJar;
	private final UIHandler _uiHandler;
	
	public UserSettings(File configDir, String username)
	{
		_configDir = new File(configDir, "users/" + username);
		_cookieHosts = new CookieHostList();
		_sessionCookies = new CookieHostList();
		_cookieExceptions = new CookieHostList();
		_blockRules = new URLRuleList();
		_blockExceptions = new URLRuleList();
		_imageRules = new URLRuleList();
		_inJar = new Vector();
		_outJar = new Vector();

		if(!_configDir.exists())
			_configDir.mkdirs();
		
		read();
		readAllRules();

		_uiHandler = new UIHandler(this, Locale.getDefault());
	}

	public CookieHostList cookieHosts()
	{
		return _cookieHosts;
	}

	public CookieHostList sessionCookies()
	{
		return _sessionCookies;
	}
	
	public CookieHostList cookieExceptions()
	{
		return _cookieExceptions;
	}
	
	public URLRuleList blockRules()
	{
		return _blockRules;
	}
	
	public URLRuleList blockExceptions()
	{
		return _blockExceptions;
	}
	
	public URLRuleList imageRules()
	{
		return _imageRules;
	}
	
	public UIHandler uiHandler()
	{
		return _uiHandler;
	}
	
	public Iterator getInCookies()
	{
		synchronized(_inJar)
		{
			return _inJar.iterator();
		}
	}
	
	public Iterator getOutCookies()
	{
		synchronized(_outJar)
		{
			return _outJar.iterator();
		}
	}
	
	public void addInJar(String host)
	{
		synchronized(_inJar)
		{
			if(_inJar.contains(host))
				_inJar.remove(host);
			_inJar.add(host);
			if(_inJar.size() > MAX_JARSIZE)
				_inJar.remove(0);
		}
	}
	
	public void addOutJar(String host)
	{
		synchronized(_outJar)
		{
			if(_outJar.contains(host))
				_outJar.remove(host);
			_outJar.add(host);
			if(_outJar.size() > MAX_JARSIZE)
				_outJar.remove(0);
		}
	}
	
	public synchronized void read()
	{
		try
		{
			File propsFile = new File(_configDir, "properties");
			if(propsFile.exists())
			{
				FileInputStream in = new FileInputStream(propsFile);
				read(in);
				in.close();
			}
		}
		catch(IOException e)
		{
			_logger.logp(Level.CONFIG, "UserSettings", "read", "Error reading properties, aborting.", e);
		}
	}
	
	public void readAllRules()
	{
		readCookieHosts();
		readSessionCookies();
		readCookieExceptions();
		readBlockRules();
		readBlockExceptions();
		readImageRules();
	}
	
	public synchronized void write()
	{
		try
		{
			File propsFile = new File(_configDir, "properties");
			FileOutputStream out = new FileOutputStream(propsFile);
			write(out, "MyProxy Properties");
			out.close();
		}
		catch(IOException e)
		{
			_logger.logp(Level.CONFIG, "UserSettings", "write", "Error writing properties, aborting.", e);
		}
	}
	
	public void writeAllRules()
	{
		writeCookieHosts();
		writeSessionCookies();
		writeCookieExceptions();
		writeBlockRules();
		writeBlockExceptions();
		writeImageRules();
	}
	
	public void refreshAllRules()
	{
		_cookieHosts.refreshOrder();
		_cookieExceptions.refreshOrder();
		_blockRules.refreshOrder();
		_blockExceptions.refreshOrder();
		_imageRules.refreshOrder();
	}
	
	public void readCookieHosts()
	{
		readCookieHostList(_cookieHosts, "cookiehosts", "cookie hosts");
	}

	public void readSessionCookies()
	{
		readCookieHostList(_sessionCookies, "sessioncookies", "session cookies");
	}
	
	public void readCookieExceptions()
	{
		readCookieHostList(_cookieExceptions, "cookieexceptions", "cookie exceptions");
	}
	
	public void writeCookieHosts()
	{
		writeCookieHostList(_cookieHosts, "cookiehosts", "cookie hosts");
	}

	public void writeSessionCookies()
	{
		writeCookieHostList(_sessionCookies, "sessioncookies", "session cookies");
	}

	public void writeCookieExceptions()
	{
		writeCookieHostList(_cookieExceptions, "cookieexceptions", "cookie exceptions");
	}
	
	private void readCookieHostList(CookieHostList list, String filename, String description)
	{
		synchronized(list)
		{
			try
			{
				File listFile = new File(_configDir, filename);
				if(listFile.exists())
				{
					FileInputStream in = new FileInputStream(listFile);
					list.read(in);
					in.close();
				}
			}
			catch(IOException e)
			{
				_logger.logp(
					Level.CONFIG,
					"UserSettings",
					"readCookieHostList",
					"Error reading " + description + ", aborting.",
					e
				);
			}
		}
	}
	
	private void writeCookieHostList(CookieHostList list, String filename, String description)
	{
		synchronized(list)
		{
			try
			{
				File listFile = new File(_configDir, filename);
				FileOutputStream out = new FileOutputStream(listFile);
				list.write(out);
				out.close();
			}
			catch(IOException e)
			{
				_logger.logp(
					Level.CONFIG,
					"UserSettings",
					"writeCookieHostList",
					"Error writing " + description + ", aborting.",
					e
				);
			}
		}
	}

	public void readBlockRules()
	{
		readURLRules(_blockRules, "blockrules", "block rules");
	}

	public void readBlockExceptions()
	{
		readURLRules(_blockExceptions, "blockexceptions", "block exceptions");
	}

	public void readImageRules()
	{
		readURLRules(_imageRules, "imagerules", "image rules");
	}

	public void writeBlockRules()
	{
		writeURLRules(_blockRules, "blockrules", "block rules");
	}

	public void writeBlockExceptions()
	{
		writeURLRules(_blockExceptions, "blockexceptions", "block exceptions");
	}

	public void writeImageRules()
	{
		writeURLRules(_imageRules, "imagerules", "image rules");
	}

	private void readURLRules(URLRuleList rules, String filename, String description)
	{
		synchronized(rules)
		{
			try
			{
				File rulesFile = new File(_configDir, filename);
				if(rulesFile.exists())
				{
					FileInputStream in = new FileInputStream(rulesFile);
					rules.read(in);
					in.close();
				}
			}
			catch(IOException e)
			{
				_logger.logp(
					Level.CONFIG,
					"UserSettings",
					"readURLRules",
					"Error reading " + description + ", aborting.",
					e
				);
			}
		}
	}

	private void writeURLRules(URLRuleList rules, String filename, String description)
	{
		synchronized(rules)
		{
			try
			{
				File rulesFile = new File(_configDir, filename);
				FileOutputStream out = new FileOutputStream(rulesFile);
				rules.write(out);
				out.close();
			}
			catch(IOException e)
			{
				_logger.logp(
					Level.CONFIG,
					"UserSettings",
					"writeURLRules",
					"Error writing " + description + ", aborting.",
					e
				);
			}
		}
	}
}

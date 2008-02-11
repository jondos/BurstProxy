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
import java.net.URLDecoder;
import java.util.*;
import java.util.logging.*;

import myproxy.filter.*;
import myproxy.httpio.*;
import org.m80.html.*;

/**
 * Generates all of the HTML user interface for MyProxy
 * and parses the submitted configuration changes.
 */
public final class UIHandler
{
	private static final Logger _logger = Logger.getLogger("myproxy.handler");
	
	private final UserSettings _settings;
	private final Template _baseTemplate;
	private final Map _templates, _defaultVars;
	private ResourceBundle _bundle;
	private Object[][] _cookieEntries, _refererEntries, _fromEntries, _agentEntries, _prefetchingEntries;
	
	public UIHandler(UserSettings settings, Locale locale)
	{
		_settings = settings;
		_baseTemplate = new Template();
		_templates = new HashMap();
		_defaultVars = new HashMap();
		setLocale(locale);
	}
	
	public byte[] handleGet(Request req, Response res, Handler handler)
	{
		URIParser uri = req.getURI();
		String data;
		
		res.clear();
		res.setVersion(1, 1);
		res.getHeaders().put("Date", HTTPDate.now());
		res.getHeaders().put("Content-Type", "text/html");
		
		String path = (uri.getPath() != null ? uri.getPath() : "/");
		if(path.equals("/"))
		{
			res.setStatus("200", "OK");
			Template tpl = (Template)_templates.get("home");
			data = fillBaseTemplate(
				getResource("TPL_HOME"), tpl.render(freshVars())
			);
		}
		else if(path.equals("/settings"))
		{
			res.setStatus("200", "OK");
			data = renderSettings(false);
		}
		else if(path.equals("/cookierules"))
		{
			res.setStatus("200", "OK");
			String query = uri.getQuery();
			if(query == null || !query.startsWith("add="))
				data = renderCookieRules(null, false);
			else
				data = renderCookieRules(query.substring(4), false);
		}
		else if(path.equals("/sessioncookies"))
		{
			res.setStatus("200", "OK");
			data = renderSessionCookies(false);
		}
		else if(path.equals("/cookieexceptions"))
		{
			res.setStatus("200", "OK");
			data = renderCookieExceptions(false);
		}
		else if(path.equals("/cookiejars"))
		{
			res.setStatus("200", "OK");
			data = renderCookieJars();
		}
		else if(path.equals("/blockrules"))
		{
			res.setStatus("200", "OK");
			String query = uri.getQuery();
			if(query == null || !query.startsWith("edit="))
				data = renderBlockRules(false);
			else
				data = renderURLRuleEditor(getResource("TPL_BLOCKRULES"), query.substring(5), _settings.blockRules(), path);
		}
		else if(path.equals("/blockexceptions"))
		{
			res.setStatus("200", "OK");
			String query = uri.getQuery();
			if(query == null || !query.startsWith("edit="))
				data = renderBlockExceptions(false);
			else
				data = renderURLRuleEditor(getResource("TPL_BLOCKEXCEPTIONS"), query.substring(5), _settings.blockExceptions(), path);
		}
		else if(path.equals("/imagerules"))
		{
			res.setStatus("200", "OK");
			String query = uri.getQuery();
			if(query == null || !query.startsWith("edit="))
				data = renderImageRules(false);
			else
				data = renderURLRuleEditor(getResource("TPL_IMAGERULES"), query.substring(5), _settings.imageRules(), path);
		}
		else if(path.equals("/shutdown"))
		{
			res.setStatus("200", "OK");
			Template tpl = (Template)_templates.get("shutdown");
			Map vars = freshVars();
			vars.put("SHUTDOWN", getResource("msg.shutdown"));
			data = tpl.render(vars);
			handler.requestShutdown();
		}
		else if(path.equals("/rule"))
		{
			res.setStatus("200", "OK");
			data = renderBlockedRequest(parseParameters(uri.getQuery()));
		}
		else
		{
			res.setStatus("404", "Not Found");
			Template tpl = (Template)_templates.get("notfound");
			Map vars = freshVars();
			vars.put("PATH", path);
			data = fillBaseTemplate(
				getResource("notfound"), tpl.render(vars)
			);
		}

		byte[] body = null;
		try
		{
			body = data.toString().getBytes("US-ASCII");
			res.getHeaders().put("Content-Length", Integer.toString(body.length));
		}
		catch(UnsupportedEncodingException e)
		{
			_logger.logp(Level.SEVERE, "UIHandler", "", "Standard encoding unsupported.", e);
		}
		
		return body;
	}

	public byte[] handlePost(Request req, Response res, byte[] entity, Handler handler)
	{
		URIParser uri = req.getURI();
		String data;
		Properties params = null;
		boolean changed;
		
		res.clear();
		res.setVersion(1, 1);
		res.getHeaders().put("Date", HTTPDate.now());
		res.getHeaders().put("Content-Type", "text/html");
		
		// parse form parameters
		String type = req.getHeaders().getValue("Content-Type");
		if(type != null && type.equalsIgnoreCase("application/x-www-form-urlencoded"))
		{
			try
			{
				params = parseParameters(new String(entity, "UTF-8"));
			}
			catch(UnsupportedEncodingException e)
			{
				_logger.logp(Level.SEVERE, "UIHandler", "", "Standard encoding unsupported.", e);
				params = new Properties();
			}
		}
		
		String path = (uri.getPath() != null ? req.getURI().getPath() : "/");
		if(path.equals("/settings"))
		{
			res.setStatus("200", "OK");
			changed = updateSettings(params);
			if(changed)
				_settings.write();
			data = renderSettings(changed);
		}
		else if(path.equals("/cookierules"))
		{
			res.setStatus("200", "OK");
			changed = updateCookieHostList(params, _settings.cookieHosts());
			if(changed)
				_settings.writeCookieHosts();
			data = renderCookieRules(null, changed);
		}
		else if(path.equals("/sessioncookies"))
		{
			res.setStatus("200", "OK");
			changed = updateCookieHostList(params, _settings.sessionCookies());
			if(changed)
				_settings.writeSessionCookies();
			data = renderSessionCookies(changed);
		}
		else if(path.equals("/cookieexceptions"))
		{
			res.setStatus("200", "OK");
			changed = updateCookieHostList(params, _settings.cookieExceptions());
			if(changed)
				_settings.writeCookieExceptions();
			data = renderCookieExceptions(changed);
		}
		else if(path.equals("/blockrules"))
		{
			res.setStatus("200", "OK");
			changed = updateURLRules(params, _settings.blockRules());
			if(changed)
				_settings.writeBlockRules();
			data = renderBlockRules(changed);
		}
		else if(path.equals("/blockexceptions"))
		{
			res.setStatus("200", "OK");
			changed = updateURLRules(params, _settings.blockExceptions());
			if(changed)
				_settings.writeBlockExceptions();
			data = renderBlockExceptions(changed);
		}
		else if(path.equals("/imagerules"))
		{
			res.setStatus("200", "OK");
			changed = updateURLRules(params, _settings.imageRules());
			if(changed)
				_settings.writeImageRules();
			data = renderImageRules(changed);
		}
		else
		{
			res.setStatus("400", "Bad Request");
			Template tpl = (Template)_templates.get("badpost");
			data = fillBaseTemplate(
				getResource("badrequest"), tpl.render(freshVars())
			);
		}
		
		byte[] body = null;
		try
		{
			body = data.getBytes("US-ASCII");
			res.getHeaders().put("Content-Length", Integer.toString(body.length));
		}
		catch(UnsupportedEncodingException e)
		{
			_logger.logp(Level.SEVERE, "UIHandler", "", "Standard encoding unsupported.", e);
		}
		
		return body;
	}
	
	public String getResource(String key)
	{
		return _bundle.getString(key);
	}
	
	private boolean updateSettings(Properties parameters)
	{
		int changes = 0;

		try
		{
			String value = parameters.getProperty("cookie.level");
			if(value != null)
			{
				int newValue = Integer.parseInt(value);
				if(_settings.getInteger("cookie.level", UserSettings.COOKIE_DEFAULT) != newValue)
				{
					_settings.setInteger("cookie.level", newValue);
					changes++;
				}
			}
			
			value = parameters.getProperty("referer.level");
			if(value != null)
			{
				int newValue = Integer.parseInt(value);
				if(_settings.getInteger("referer.level", UserSettings.REFERER_DEFAULT) != newValue)
				{
					_settings.setInteger("referer.level", newValue);
					changes++;
				}
			}
			
			value = parameters.getProperty("from.level");
			if(value != null)
			{
				int newValue = Integer.parseInt(value);
				if(_settings.getInteger("from.level", UserSettings.FROM_DEFAULT) != newValue)
				{
					_settings.setInteger("from.level", newValue);
					changes++;
				}
			}
			
			value = parameters.getProperty("from.value");
			if(value != null)
			{
				if(!_settings.get("from.value", UserSettings.FROM_DEFAULTFAKE).equals(value))
				{
					_settings.set("from.value", value);
					changes++;
				}
			}
			
			value = parameters.getProperty("agent.level");
			if(value != null)
			{
				int newValue = Integer.parseInt(value);
				if(_settings.getInteger("agent.level", UserSettings.AGENT_DEFAULT) != newValue)
				{
					_settings.setInteger("agent.level", newValue);
					changes++;
				}
			}

			value = parameters.getProperty("agent.value");
			if(value != null)
			{
				if(!_settings.get("agent.value", UserSettings.AGENT_DEFAULTFAKE).equals(value))
				{
					_settings.set("agent.value", value);
					changes++;
				}
			}

			value = parameters.getProperty("prefetching.value");
			if(value != null) {
				int newValue = Integer.parseInt(value);
				if(_settings.getInteger("prefetching.value", UserSettings.PREFETCHING_DEFAULT) != newValue) {
					_settings.setInteger("prefetching.value", newValue);
					changes++;
				}
			}
			
			value = parameters.getProperty("prefetching.remoteaddress");
			if(value != null)
			{
				if(!_settings.get("prefetching.remoteaddress", UserSettings.PREFETCHING_DEFAULT_REMOTE_ADDRESS).equals(value))
				{
					_settings.set("prefetching.remoteaddress", value);
					changes++;
				}
			}
		}
		catch(NumberFormatException e)
		{
			_logger.logp(Level.WARNING, "UIHandler", "updateSettings", "Invalid parameter, aborting update.", e);
		}
		
		return changes > 0;
	}
	
	private boolean updateCookieHostList(Properties parameters, CookieHostList hosts)
	{
		String action = parameters.getProperty("action");
		if(action == null)
			return false;
		
		if(action.equals("add"))
		{
			String newHost = parameters.getProperty("host", "");
			if(newHost.length() == 0)
				return false;
			
			String exact = parameters.getProperty("exact", "false");
			if(exact.equals("true"))
				hosts.add(newHost, true);
			else
				hosts.add(newHost, false);
			
			return true;
		}
		else if(action.equals("delete"))
		{
			String ids = parameters.getProperty("id", "");
			if(ids.length() == 0)
				return false;
				
			int deletions = 0;
			StringTokenizer t = new StringTokenizer(ids, ",");
			try
			{
				while(t.hasMoreTokens())
				{
					if(hosts.remove(Integer.parseInt(t.nextToken())))
						deletions++;
				}				
			}
			catch(NumberFormatException e)
			{
				_logger.logp(Level.WARNING, "UIHandler", "updateCookieHostList", "Invalid ID, aborting update.", e);
			}
			
			if(deletions > 0)
				return true;
		}

		return false;
	}
	
	private boolean updateURLRules(Properties parameters, URLRuleList rules)
	{
		String action = parameters.getProperty("action");
		if(action == null)
			return false;
		
		if(action.equals("add") || action.equals("replace"))
		{
			String hostPart = parameters.getProperty("hostpart", "");
			String pathPart = parameters.getProperty("pathpart", "");
			String comment = parameters.getProperty("comment", "");
			
			if(hostPart.length() == 0)
				hostPart = null;
			if(pathPart.length() == 0)
				pathPart = null;
			if(comment.length() == 0)
				comment = null;
			
			if(hostPart == null && pathPart == null)
				return false;
			
			if(action.equals("add"))
			{
				rules.add(hostPart, pathPart, comment);
				return true;
			}
			else
			{
				int id = -1;
				
				try
				{
					id = Integer.parseInt(parameters.getProperty("id", "-1"));
				}
				catch(NumberFormatException e)
				{
					return false;
				}
				
				return rules.replace(id, hostPart, pathPart, comment);
			}
		}
		else if(action.equals("delete"))
		{
			String ids = parameters.getProperty("id", "");
			if(ids.length() == 0)
				return false;
				
			int deletions = 0;
			StringTokenizer t = new StringTokenizer(ids, ",");
			try
			{
				while(t.hasMoreTokens())
				{
					if(rules.remove(Integer.parseInt(t.nextToken())))
						deletions++;
				}				
			}
			catch(NumberFormatException e)
			{
				_logger.logp(Level.WARNING, "UIHandler", "updateURLRules", "Invalid ID, aborting update.", e);
			}
			
			if(deletions > 0)
				return true;
		}

		return false;
	}
	
	private String renderSettings(boolean saved)
	{
		Template tpl = (Template)_templates.get("settings");
		Map vars = freshVars();
		
		if(saved)
			vars.put("SAVED", getResource("msg.saved"));
		
		int current;
		// cookies
		current = _settings.getInteger("cookie.level", UserSettings.COOKIE_DEFAULT);
		vars.put(
			"COOKIE_ROWS",
			fillRadioGroup("cookie.level", _cookieEntries, current)
		);
		// referer
		current = _settings.getInteger("referer.level", UserSettings.REFERER_DEFAULT);
		vars.put(
			"REFERER_ROWS",
			fillRadioGroup("referer.level", _refererEntries, current)
		);
		// from
		current = _settings.getInteger("from.level", UserSettings.FROM_DEFAULT);
		vars.put(
			"FROM_ROWS",
			fillRadioGroup("from.level", _fromEntries, current)
		);
		vars.put(
			"FROM_VALUE",
			_settings.get("from.value", UserSettings.FROM_DEFAULTFAKE)
		);
		// agent
		current = _settings.getInteger("agent.level", UserSettings.AGENT_DEFAULT);
		vars.put(
			"AGENT_ROWS",
			fillRadioGroup("agent.level", _agentEntries, current)
		);
		vars.put(
			"AGENT_VALUE",
			_settings.get("agent.value", UserSettings.AGENT_DEFAULTFAKE)
		);
		// prefetching
		current = _settings.getInteger("prefetching.value", UserSettings.PREFETCHING_DEFAULT);
		vars.put(
			"PREFETCHING_ROWS",
			fillRadioGroup("prefetching.value", _prefetchingEntries, current)
		);
		vars.put(
				"PREFETCHING_REMOTEADDRESS",
				_settings.get("prefetching.remoteaddress", UserSettings.PREFETCHING_DEFAULT_REMOTE_ADDRESS)
		);

		
		return fillBaseTemplate(getResource("TPL_SETTINGS"), tpl.render(vars));
	}

	private String fillRadioGroup(String name, Object[] entries, int checked)
	{
		Template tpl = (Template)_templates.get("settingrow");
		StringBuffer group = new StringBuffer();
		
		for(int i = 0; i < entries.length; i++)
		{
			Object[] rowValues = (Object[])entries[i];
			
			tpl.clear();
			tpl.set("NAME", name);
			tpl.set("VALUE", rowValues[0]);
			if(((Integer)rowValues[0]).intValue() == checked)
				tpl.set("CHECKED", "checked");
			tpl.set("TEXT", rowValues[1]);
			
			group.append(tpl.render());
			if(i != entries.length-1)
				group.append("\n");
		}
		
		return group.toString();
	}
	
	private String renderCookieRules(String addHost, boolean saved)
	{
		return renderCookieHostList(
			"cookierules",
			_settings.cookieHosts(),
			addHost,
			"/cookierules",
			getResource("TPL_COOKIERULES"),
			saved
		);
	}

	private String renderSessionCookies(boolean saved)
	{
		return renderCookieHostList(
			"sessioncookies",
			_settings.sessionCookies(),
			null,
			"/sessioncookies",
			getResource("TPL_SESSIONCOOKIES"),
			saved
		);
	}
	
	private String renderCookieExceptions(boolean saved)
	{
		return renderCookieHostList(
			"cookieexceptions",
			_settings.cookieExceptions(),
			null,
			"/cookieexceptions",
			getResource("TPL_COOKIEEXCEPTIONS"),
			saved
		);
	}

	private String renderCookieHostList(String template, CookieHostList hosts, String addHost, String action, String section, boolean saved)
	{
		Template tpl = (Template)_templates.get(template);
		Map vars = freshVars();
		
		if(saved)
			vars.put("SAVED", getResource("msg.saved"));
		vars.put("RULES", fillCookieHostList(hosts, addHost, action));
		
		return fillBaseTemplate(section, tpl.render(vars));
	}
		
	private String fillCookieHostList(CookieHostList hosts, String addHost, String action)
	{
		Template tpl = (Template)_templates.get("cookiehostlist");
		Map vars = freshVars();
		
		vars.put("ACTION", action);

		Template row = (Template)_templates.get("cookierow");
		StringBuffer rows = new StringBuffer();
		for(Iterator i = hosts.sortedIterator(); i.hasNext();)
		{
			CookieHost host = (CookieHost)i.next();
			
			row.clear();
			row.set("PATTERN", host.getSource());
			if(host.isExactMatch())
				row.set("EXACTMATCH", getResource("TPL_YES"));
			else
				row.set("EXACTMATCH", getResource("TPL_NO"));
			row.set("ID", host.getID());
			
			rows.append(row.render());
			if(i.hasNext())
				rows.append("\n");
		}
		
		vars.put("ROWS", rows.toString());
		if(addHost != null)
			vars.put("ADDHOST", addHost);
		
		return tpl.render(vars);
	}

	private String renderCookieJars()
	{
		Template tpl = (Template)_templates.get("cookiejars");
		Map vars = freshVars();
		
		vars.put("INROWS", fillJar(_settings.getInCookies()));
		vars.put("OUTROWS", fillJar(_settings.getOutCookies()));
		vars.put("JARSIZE", Integer.toString(UserSettings.MAX_JARSIZE));
		
		return fillBaseTemplate(getResource("TPL_COOKIEJARS"), tpl.render(vars));
	}
	
	private String fillJar(Iterator hosts)
	{
		Template row = (Template)_templates.get("jarrow");
		StringBuffer rows = new StringBuffer();

		while(hosts.hasNext())
		{
			row.clear();
			row.set("HOST", (String)hosts.next());
			row.set("TPL_ADD", getResource("TPL_ADD"));
			
			rows.append(row.render());
			if(hosts.hasNext())
				rows.append("\n");
		}
		
		return rows.toString();
	}
	
	private String renderBlockRules(boolean saved)
	{
		return renderURLRules(
			"blockrules",
			_settings.blockRules(),
			"/blockrules",
			getResource("TPL_BLOCKRULES"),
			saved
		);
	}
	
	private String renderBlockExceptions(boolean saved)
	{
		return renderURLRules(
			"blockexceptions",
			_settings.blockExceptions(),
			"/blockexceptions",
			getResource("TPL_BLOCKEXCEPTIONS"),
			saved
		);
	}
	
	private String renderImageRules(boolean saved)
	{
		return renderURLRules(
			"imagerules",
			_settings.imageRules(),
			"/imagerules",
			getResource("TPL_IMAGERULES"),
			saved
		);
	}
	
	private String renderURLRules(String template, URLRuleList rules, String action, String section, boolean saved)
	{
		Template tpl = (Template)_templates.get(template);
		Map vars = freshVars();

		if(saved)
			vars.put("SAVED", getResource("msg.saved"));
		vars.put("RULES", fillURLRuleList(rules, action));
		
		return fillBaseTemplate(section, tpl.render(vars));
	}
	
	private String fillURLRuleList(URLRuleList rules, String action)
	{
		Template tpl = (Template)_templates.get("urlrulelist");
		Map vars = freshVars();
		
		vars.put("ACTION", action);

		Template row = (Template)_templates.get("urlrow");
		StringBuffer rows = new StringBuffer();
		for(Iterator i = rules.sortedIterator(); i.hasNext();)
		{
			URLRule rule = (URLRule)i.next();
			String hostPart = (rule.getHostPart() != null ? rule.getHostPart() : "&nbsp;");
			String pathPart = (rule.getPathPart() != null ? rule.getPathPart() : "&nbsp;");
			String comment = (rule.getComment() != null ? rule.getComment() : "&nbsp;");
			
			row.clear();
			row.set("HOSTPART", hostPart);
			row.set("PATHPART", pathPart);
			row.set("COMMENT", comment);
			row.set("ID", rule.getID());
			row.set("ACTION", action);
			row.set("TPL_EDIT", getResource("TPL_EDIT"));
			
			rows.append(row.render());
			if(i.hasNext())
				rows.append("\n");
		}
		
		vars.put("ROWS", rows.toString());
		
		return tpl.render(vars);
	}
	
	private String renderURLRuleEditor(String section, String idString, URLRuleList rules, String action)
	{
		int id = -1;

		if(idString.length() > 0)
		{
			try
			{
				id = Integer.parseInt(idString);
			}
			catch(NumberFormatException e)
			{
				return fillBaseTemplate(section, "<b>Invalid rule ID</b>");
			}
		}
		
		URLRule rule = rules.get(id);
		if(rule == null)
			return fillBaseTemplate(section, "<b>No such rule</b>");
		
		Template tpl = (Template)_templates.get("ruleeditor");
		Map vars = freshVars();

		vars.put("SECTION", section);
		vars.put("ACTION", action);
		if(rule.getHostPart() != null)
			vars.put("HOSTPART", rule.getHostPart());
		if(rule.getPathPart() != null)
			vars.put("PATHPART", rule.getPathPart());
		if(rule.getComment() != null)
			vars.put("COMMENT", rule.getComment());
		vars.put("ID", Integer.toString(rule.getID()));
				
		return fillBaseTemplate(section, tpl.render(vars));
	}
	
	private String renderBlockedRequest(Properties params)
	{
		int id = -1;

		if(params.getProperty("id").length() > 0)
		{
			try
			{
				id = Integer.parseInt(params.getProperty("id"));
			}
			catch(NumberFormatException e)
			{
				return fillBaseTemplate(
					getResource("blocked"), "<b>Invalid rule ID</b>"
				);
			}
		}

		URLRule rule = _settings.blockRules().get(id);
		if(rule == null)
		{
			return fillBaseTemplate(
				getResource("blocked"), "<b>No such rule</b>"
			);
		}
		
		StringBuffer url = new StringBuffer(params.getProperty("url"));
		int pos;
		while((pos = url.indexOf(" amp; ")) != -1)
			url.replace(pos, pos + 6, "&amp;");
		while((pos = url.indexOf(" quest; ")) != -1)
			url.replace(pos, pos + 8, "?");

		Template tpl = (Template)_templates.get("blocked");
		Map vars = freshVars();
		
		vars.put("TITLE", getResource("blocked"));
		vars.put("REQUEST", url.toString());
		if(rule.getHostPart() != null)
			vars.put("HOSTPART", rule.getHostPart());
		if(rule.getPathPart() != null)
			vars.put("PATHPART", rule.getPathPart());
		if(rule.getComment() != null)
			vars.put("COMMENT", rule.getComment());
		
		return tpl.render(vars);
	}

	private Properties parseParameters(String parameterString)
	{
		Properties parameters = new Properties();
		
		try
		{
			StringTokenizer t = new StringTokenizer(parameterString, "&");
			while(t.hasMoreTokens())
			{
				String p = t.nextToken();
				String key, value;
				
				int pos = p.indexOf('=');
				if(pos == -1)
				{
					key = URLDecoder.decode(p, "UTF-8");
					value = "";
				}
				else if(pos == p.length() - 1)
				{
					key = URLDecoder.decode(p.substring(0, p.length() - 1), "UTF-8");
					value = "";
				}
				else
				{
					key = URLDecoder.decode(p.substring(0, pos), "UTF-8");
					value = URLDecoder.decode(p.substring(pos + 1), "UTF-8");
				}
				
				if(parameters.get(key) == null)
				{
					parameters.setProperty(key, value);
				}
				else
				{
					if(value.length() > 0)
						parameters.setProperty(key, parameters.getProperty(key) + "," + value);
				}
			}
		}
		catch(UnsupportedEncodingException e)
		{
			_logger.logp(Level.SEVERE, "UIHandler", "", "Standard encoding unsupported.", e);
		}
		
		return parameters;
	}
	
	private String fillBaseTemplate(String section, String content)
	{
		Map vars = freshVars();
		vars.put("SECTION", section);
		vars.put("CONTENT", content);
		
		return _baseTemplate.render(vars);		
	}

	private Map freshVars()
	{
		return new HashMap(_defaultVars);
	}

	private void setLocale(Locale locale)
	{
		Template tpl = new Template();

		_bundle = ResourceBundle.getBundle("myproxy.UIBundle", locale);
		
		tpl.setSource(getResource("setting.cookie.check"));
		tpl.set("TPL_COOKIERULES", getResource("TPL_COOKIERULES"));
		_cookieEntries = new Object[][] {
			{ new Integer(UserSettings.COOKIE_PASS), getResource("setting.cookie.pass") },
			{ new Integer(UserSettings.COOKIE_CHECK), tpl.render() },
			{ new Integer(UserSettings.COOKIE_EAT), getResource("setting.cookie.eat") }
		};
		
		_refererEntries = new Object[][] {
			{ new Integer(UserSettings.REFERER_PASS), getResource("setting.referer.pass") },
			{ new Integer(UserSettings.REFERER_TRIM), getResource("setting.referer.trim") },
			{ new Integer(UserSettings.REFERER_EAT), getResource("setting.referer.eat") }
		};
		
		_fromEntries = new Object[][] {
			{ new Integer(UserSettings.FROM_PASS), getResource("setting.from.pass") },
			{ new Integer(UserSettings.FROM_FAKE), getResource("setting.from.fake") },
			{ new Integer(UserSettings.FROM_EAT), getResource("setting.from.eat") }
		};
		
		_agentEntries = new Object[][] {
			{ new Integer(UserSettings.AGENT_PASS), getResource("setting.agent.pass") },
			{ new Integer(UserSettings.AGENT_FAKE), getResource("setting.agent.fake") },
			{ new Integer(UserSettings.AGENT_EAT), getResource("setting.agent.eat") }
		};
		
		_prefetchingEntries = new Object[][] {
			{ new Integer(UserSettings.PREFETCHING_DISABLED), getResource("setting.prefetching.disabled") },
			{ new Integer(UserSettings.PREFETCHING_LOCALEND), getResource("setting.prefetching.localend") },
			{ new Integer(UserSettings.PREFETCHING_REMOTEEND), getResource("setting.prefetching.remoteend") }
		};
		
		Enumeration keys = _bundle.getKeys();
		_defaultVars.clear();
		while(keys.hasMoreElements())
		{
			String key = (String)keys.nextElement();
			if(key.startsWith("TPL_"))
				_defaultVars.put(key, getResource(key));
		}
		
		StringBuffer tplPath = new StringBuffer("templates/"); 
		String localDir = locale.getLanguage();

		tplPath.append(localDir);
		if(
			localDir.equals("") ||
			getClass().getResource(tplPath.toString()) == null
		)
			localDir = "en";
		
		String[] tplNames = {
			"base", "home", "settings", "settingrow",
			"cookierules", "sessioncookies", "cookieexceptions",
			"cookiehostlist", "cookierow", "cookiejars", "jarrow",
			"blockrules", "blockexceptions", "imagerules",
			"urlrulelist", "urlrow", "ruleeditor",
			"blocked", "shutdown"
		};
		
		_templates.clear();
		for(int i = 0; i < tplNames.length; i++)
		{
			tplPath.delete(0, tplPath.length());
			tplPath.append("templates/").append(tplNames[i]).append(".tpl");
			if(getClass().getResource(tplPath.toString()) == null)
				tplPath.insert(10, '/').insert(10, localDir);
			
			tpl = new Template();
			try
			{
				String source = readTextFile(getClass().getResourceAsStream(tplPath.toString()));
				
				if(!tplNames[i].equals("base"))
					tpl.setSource(source);
				else
					_baseTemplate.setSource(source);
			}
			catch(IOException e)
			{
				_logger.logp(Level.SEVERE, "UIHandler", "", "Error reading " + tplPath.toString(), e);
			}
			
			_templates.put(tplNames[i], tpl);
		}
		
		tpl = new Template();
		tpl.setSource(getResource("tpl.notfound"));
		_templates.put("notfound", tpl);
		
		tpl = new Template();
		tpl.setSource(getResource("tpl.badpost"));
		_templates.put("badpost", tpl);
	}

	private String readTextFile(InputStream in) throws IOException
	{
		InputStreamReader reader = new InputStreamReader(in);
		char[] buf = new char[4096];
		int count;
		StringBuffer content = new StringBuffer();
		
		while((count = reader.read(buf)) != -1)
			content.append(buf, 0, count);
		
		return content.toString();
	}
}

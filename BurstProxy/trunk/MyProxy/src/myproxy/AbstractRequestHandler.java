/**
 * 
 */
package myproxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import myproxy.filter.URLRule;
import myproxy.httpio.ChunkedInputStream;
import myproxy.httpio.ChunkedOutputStream;
import myproxy.httpio.Headers;
import myproxy.httpio.Message;
import myproxy.httpio.Request;
import myproxy.httpio.Response;
import myproxy.httpio.URIParser;

/**
 * @author dh
 *
 */
public abstract class AbstractRequestHandler implements RequestHandler
{
	protected static final Logger _logger = Logger.getLogger("myproxy.handler");
	protected static final Logger _msgLogger = Logger.getLogger("myproxy.messages");
	protected static final Logger _cookieLogger = Logger.getLogger("myproxy.cookies");
	

	protected Handler _handler;
	protected Connection _client;
	protected Connection _server;
	
	protected Request _req;
	protected Response _res;
	protected URIParser _uri;
	protected Headers _reqHeaders;
	protected Headers _resHeaders;
	
	protected UserSettings _settings;
	protected final MyProxy _controller;
	
	protected final String _localURL, _gifURL, _forwardHostKey;
	protected final Map _servers;
	private final String _name;
	
	protected static final int SERVER_COMM_TIMEOUT = 2 * 60 * 1000;
	protected static final Pattern NUMERIC_IP4 = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d+)?");
	protected static final Pattern COOKIE_SEP  = Pattern.compile("[ \\t]*;[ \\t]*");
	
	public AbstractRequestHandler(MyProxy controller, Handler handler) {
		_controller = controller;
		_handler = handler;
		_name = handler.getName();
		_req = handler.getRequest();
		_res = handler.getResponse();
		_reqHeaders = _req.getHeaders();
		_resHeaders = _res.getHeaders();
		_uri = _req.getURI();
		_settings = _controller.getSettings("default");
		
		_servers = new HashMap();
		
		_client = _handler.getClientConnection();
		
		StringBuffer tmp = new StringBuffer("http://");
		tmp.append(_controller.getLocalAddress().getHostName());
		if(_controller.getLocalAddress().getPort() != 80)
			tmp.append(':').append(_controller.getLocalAddress().getPort());
		_localURL = tmp.toString();
		
		tmp.append("/res/blank.gif");
		_gifURL = tmp.toString();
		
		if(_controller.getForwardAddress() != null)
		{
			tmp = new StringBuffer(_controller.getForwardAddress().getHostName());
			tmp.append(':').append(_controller.getForwardAddress().getPort());
			_forwardHostKey = tmp.toString();
		}
		else
		{
			_forwardHostKey = null;
		}

	}
	
	public void reuseHandler(MyProxy controller, Handler handler)
	{
		_handler = handler;
		_req = handler.getRequest();
		_res = handler.getResponse();
		_reqHeaders = _req.getHeaders();
		_resHeaders = _res.getHeaders();
		_uri = _req.getURI();
		_settings = _controller.getSettings("default");
		
		_client = _handler.getClientConnection();
	}
	
	/**
	 * Unifies a log message with an HTTP message start line.
	 * If logging at FINEST, the HTTP headers will be appended.
	 */
	protected void logHTTPMessage(String message, Message httpMessage)
	{
		StringBuffer logEntry = new StringBuffer(message);
		
		logEntry.append(" (").append(httpMessage).append(")");
		if(_msgLogger.isLoggable(Level.FINEST))
		{
			for(Iterator i = httpMessage.getHeaders().iterator(); i.hasNext();)
				logEntry.append("\n\t").append(i.next());
			for(Iterator i = httpMessage.getHeaders().getCookies().iterator(); i.hasNext();)
				logEntry.append("\n\t").append(i.next());
		}
		
		_msgLogger.logp(Level.FINER, getName(), "handleRequest", logEntry.toString());
	}

	protected void copyStream(InputStream in, OutputStream out, int length) throws IOException
	{
		int sizeOfBuffer = 4096;
		byte[] buffer = new byte[sizeOfBuffer];
		int read = 0;
		
		if(length != -1)
		{
			while(length > 0)
			{
				read = in.read(buffer, 0, (length>sizeOfBuffer)?sizeOfBuffer:length);
				if(read == -1)
					throw new IOException("Unexpected end of stream.");
				out.write(buffer, 0, read);
				length -= read;
			}
		}
		else
		{
			while((read = in.read(buffer)) != -1)
				out.write(buffer, 0, read);
		}
		out.flush();
	}
	
	protected boolean requestIsBlocked() throws IOException, HTTPException
	{
		String hostPart = _reqHeaders.getValue("Host");
		String pathPart = _req.getFullURIPath();
		
		Matcher matcher = NUMERIC_IP4.matcher(hostPart);
		if(matcher.matches())
		{
			try
			{
				InetAddress address = InetAddress.getByName(matcher.group(1));
				hostPart = address.getCanonicalHostName();
			}
			catch(UnknownHostException e)
			{
				throw new HTTPException("404", "err.unknownhost", matcher.group(1), _req.hasBodyHeaders());
			}
		}
		
		URLRule rule = _settings.blockRules().match(hostPart, pathPart);
		if(rule != null)
		{
			if(_settings.blockExceptions().match(hostPart, pathPart) != null)
				return false;
			
			_res.clear();
			_res.setVersion(1, 1);

			byte[] bytes = null;
			
			if(_settings.imageRules().match(hostPart, pathPart) != null)
			{
				_res.setStatus("307", "Temporary Redirect");
				_resHeaders.put("Location", _gifURL);
				_resHeaders.put("Content-Length", "0");
			}
			else
			{
				_res.setStatus("403", "Forbidden");
				
				StringBuffer body = new StringBuffer();
				body.append("<html><body><a href=\"");
				body.append(_localURL).append("/rule?id=").append(rule.getID());
				body.append("&url=");
				
				StringBuffer url = new StringBuffer();
				if(_uri.getHost() != null)
				{
					url.append(_uri.getSource());
				}
				else
				{
					url.append("http://");
					url.append(hostPart);
					if(pathPart != null)
						url.append(pathPart);
					else
						url.append('/');
				}
				
				// hide '?' and '&' in the request URL
				int pos;
				while((pos = url.indexOf("&")) != -1)
					url.replace(pos, pos + 1, " amp; ");
				while((pos = url.indexOf("?")) != -1)
					url.replace(pos, pos + 1, " quest; ");
				
				body.append(URLEncoder.encode(url.toString(), "UTF-8"));
				body.append("\"><font size=\"-1\">");
				body.append(_settings.uiHandler().getResource("blocked"));
				body.append("</font></a></body></html>");
				bytes = body.toString().getBytes("US-ASCII");
				
				_resHeaders.put("Content-Type", "text/html");
				_resHeaders.put("Content-Length", Integer.toString(bytes.length));
			}

			_client.write(_res);
			if(bytes != null)
			{
				_client.out.write(bytes);
				_client.out.flush();
			}

			return true;
		}
		
		return false;
	}
	
	
	protected void getServerConnection() throws HTTPException, IOException
	{
		String hostname, key;
		int port;
		
		if(_forwardHostKey == null)
		{
			hostname = getOriginServer();
			port = getOriginPort();
			
			StringBuffer tmp = new StringBuffer(hostname);
			tmp.append(':').append(port);
			key = tmp.toString();
		}
		else
		{
			hostname = _controller.getForwardAddress().getHostName();
			port = _controller.getForwardAddress().getPort();
			
			key = _forwardHostKey;
		}
		
		_server = (Connection)_servers.get(key);
		
		if(_server != null && !_server.isClosed())
		{
			// see if it's still alive
			int oldTimeout = _server.getTimeout();
			try
			{
				_server.setTimeout(1);
				_server.in.read();
				// read() returning means either the socket was
				// closed on the other end (-1), or there was
				// some crap in the socket
				_server.safeClose();
				_servers.remove(key);
			}
			catch(SocketTimeoutException e)
			{
				// it's alive and clean
				_server.setTimeout(oldTimeout);
				_logger.finer("Reusing remote server connection to "+hostname+":"+port);
				return;
			}
			catch(IOException e) // in case an IOException occured we close the socket as well
			{
				_server.safeClose();
				_servers.remove(key);
			}
		}
		
		try
		{
			_logger.finer("Creating new remote server connection to "+hostname+":"+port);
			
			// experimental, DNS problem still happens
			InetAddress address = InetAddress.getByName(hostname);
			
			_server = new Connection(new Socket(address, port));
			_server.setTimeout(SERVER_COMM_TIMEOUT);
		}
		catch(UnknownHostException e)
		{
			throw new HTTPException("404", "err.unknownhost", hostname, _req.hasBodyHeaders());
		}
		catch(NoRouteToHostException e)
		{
			throw new HTTPException("502", "err.noroute", hostname, _req.hasBodyHeaders());
		}
		catch(ConnectException e)
		{
			StringBuffer detail = new StringBuffer(hostname);
			if(port != 80)
				detail.append(':').append(port);
			throw new HTTPException("502", "err.hostconnect", detail.toString(), _req.hasBodyHeaders());
		}
		catch(PortUnreachableException e)
		{
			throw new HTTPException("502", "err.portconnect", Integer.toString(port), _req.hasBodyHeaders());
		}
		
		_servers.put(key, _server);
	}
	
	
	/**
	 * Returns the host to connect to, the authority part
	 * in the request URL overrides the "Host" header.
	 */
	protected String getOriginServer()
	{
		if(_uri.getHost() != null)
			return _uri.getHost();
		
		String hostHeader = _reqHeaders.getValue("Host");
		int pos;
		if((pos = hostHeader.indexOf(':')) != -1)
			return hostHeader.substring(0, pos);
		else
			return hostHeader;
	}

	
	/**
	 * Returns the port to connect to, the authority part
	 * in the request URL overrides the "Host" header.
	 * 
	 * @return <tt>80</tt> if no port was specified
	 */
	protected int getOriginPort()
	{
		if(_uri.getPort() != -1)
			return _uri.getPort();
			
		String hostHeader = _reqHeaders.getValue("Host");
		int pos;
		if((pos = hostHeader.indexOf(':')) != -1)
			return Integer.parseInt(hostHeader.substring(pos + 1));
		
		if(_uri.getScheme()!=null && _uri.getScheme().equalsIgnoreCase("https"))
			return 443;
		return 80;
	}
	
	protected boolean isLocalRequest(String hostname, int port) throws HTTPException
	{
		InetSocketAddress dst = new InetSocketAddress(hostname, port);
		if(dst.equals(_controller.getLocalAddress()))
			return true;
		
		if(
			(_req.getMethod().equals("OPTIONS") || _req.getMethod().equals("TRACE")) &&
			(_reqHeaders.contains("Max-Forwards") && _reqHeaders.getValue("Max-Forwards").equals("0"))
		)
				return true;

		return false;
	}

	
	protected void touchRequestHeaders()
	{
		List cookies = _reqHeaders.getCookies();
		if(cookies.size() > 0)
		{
			switch(_settings.getInteger("cookie.level", UserSettings.COOKIE_DEFAULT))
			{
				case UserSettings.COOKIE_PASS:
					// do nothing
					break;
				case UserSettings.COOKIE_CHECK:
					String host = _reqHeaders.getValue("Host");
					
					boolean regularCookie = false, sessionCookie;
					
					sessionCookie = _settings.sessionCookies().match(host, false) != null;
					if(!sessionCookie)
						regularCookie = _settings.cookieHosts().match(host, false) != null;
					
					if(
						(regularCookie || sessionCookie) &&
						_settings.cookieExceptions().match(host, false) == null
					)
					{
						if(_cookieLogger.isLoggable(Level.FINER))
						{
							StringBuffer message = new StringBuffer("Passing outgoing cookie(s) for ");
							message.append(host);
							for(Iterator i = cookies.iterator(); i.hasNext();)
							{
								String cookie = (String)i.next();
								cookie = cookie.substring(8); // skip "Cookie: "
								message.append("\n\t").append(cookie);
							}
							_cookieLogger.logp(Level.FINER, getName(), "touchRequestHeaders", message.toString());
						}
						break;
					}

					if(_cookieLogger.isLoggable(Level.FINE))
					{
						StringBuffer message = new StringBuffer("Eating outgoing cookie(s) for ");
						message.append(host);
						for(Iterator i = cookies.iterator(); i.hasNext();)
						{
							String cookie = (String)i.next();
							cookie = cookie.substring(8); // skip "Cookie: "
							message.append("\n\t").append(cookie);
						}
						_cookieLogger.logp(Level.FINE, getName(), "touchRequestHeaders", message.toString());
					}
					
					_settings.addOutJar(host);
					cookies.clear();
					break;
				case UserSettings.COOKIE_EAT:
					cookies.clear();
					break;
			}
		}

		if(_reqHeaders.contains("Referer"))
		{
			switch(_settings.getInteger("referer.level", UserSettings.REFERER_DEFAULT))
			{
				case UserSettings.REFERER_PASS:
					// do nothing
					break;
				case UserSettings.REFERER_TRIM:
					StringBuffer referer = new StringBuffer("http://");
					referer.append(_reqHeaders.getValue("Host")).append('/');
					_reqHeaders.put("Referer", referer.toString());
					break;
				case UserSettings.REFERER_EAT:
					_reqHeaders.put("Referer", null);
					break;
			}
		}

		if(_reqHeaders.contains("From"))
		{
			switch(_settings.getInteger("from.level", UserSettings.FROM_DEFAULT))
			{
				case UserSettings.FROM_PASS:
					// do nothing
					break;
				case UserSettings.FROM_FAKE:
					_reqHeaders.put("From", _settings.get("from.value", UserSettings.FROM_DEFAULTFAKE));
					break;
				case UserSettings.FROM_EAT:
					_reqHeaders.put("From", null);
					break;
			}
		}

		if(_reqHeaders.contains("User-Agent"))
		{
			switch(_settings.getInteger("agent.level", UserSettings.AGENT_DEFAULT))
			{
				case UserSettings.AGENT_PASS:
					// do nothing
					break;
				case UserSettings.AGENT_FAKE:
					_reqHeaders.put("User-Agent", _settings.get("agent.value", UserSettings.AGENT_DEFAULTFAKE));
					break;
				case UserSettings.AGENT_EAT:
					_reqHeaders.put("User-Agent", null);
					break;
			}
		}
	}

	protected void touchResponseHeaders()
	{
		List cookies = _resHeaders.getCookies();
		if(cookies.size() > 0)
		{
			switch(_settings.getInteger("cookie.level", UserSettings.COOKIE_DEFAULT))
			{
				case UserSettings.COOKIE_PASS:
					// do nothing
					break;
				case UserSettings.COOKIE_CHECK:
					List sessionCookies = new Vector();
					
					for(Iterator i = cookies.iterator(); i.hasNext();)
					{
						String cookie = (String)i.next();
						cookie = cookie.substring(12); // remove "Set-Cookie: "
						String[] fields = COOKIE_SEP.split(cookie);
						String host = _reqHeaders.getValue("Host");
						String cookieDomain = host;
						
						// lowercase the optional field names
						for(int j = 1; j < fields.length; j++)
						{
							String[] parts = fields[j].split("=", 2);
							
							if(parts.length == 2)
							{
								StringBuffer newCookie = new StringBuffer();							
								newCookie.append(parts[0].toLowerCase());
								newCookie.append('=').append(parts[1]);								
								fields[j] = newCookie.toString();
							}
							else
							{
								StringBuffer message = new StringBuffer("Invalid cookie field from ");
								message.append(host).append("\n\t").append(fields[j]);
								_cookieLogger.logp(Level.WARNING, getName(), "touchResponseHeaders", message.toString());
							}
						}
						
						for(int j = 1; j < fields.length; j++)
						{
							if(fields[j].startsWith("domain="))
							{
								cookieDomain = fields[j].substring(7);
								break;
							}
						}
						
						boolean regularCookie = false, sessionCookie;
						
						sessionCookie = _settings.sessionCookies().match(cookieDomain, true) != null;
						if(!sessionCookie)
							regularCookie = _settings.cookieHosts().match(cookieDomain, true) != null;						
						
						if(
							// first see if this cookie (domain) is generally allowed,
							// then check if the host would be allowed to receive it
							// (that's why incoming = false)
							(regularCookie || sessionCookie) &&
							_settings.cookieExceptions().match(host, false) == null
						)
						{
							if(sessionCookie)
							{
								StringBuffer newCookie = new StringBuffer();

								newCookie.append("Set-Cookie: ").append(fields[0]);
								
								for(int j = 1; j < fields.length; j++)
								{
									if(!fields[j].startsWith("expires="))
										newCookie.append("; ").append(fields[j]);
								}
								
								sessionCookies.add(newCookie.toString());
								i.remove();
								
								cookie = newCookie.substring(12); // remove "Set-Cookie: "
							}
							
							if(_cookieLogger.isLoggable(Level.FINER))
							{
								StringBuffer message = new StringBuffer("Passing incoming cookie from ");
								message.append(host).append("\n\t").append(cookie);
								_cookieLogger.logp(Level.FINER, getName(), "touchResponseHeaders", message.toString());
							}
							
							continue;
						}
						
						if(_cookieLogger.isLoggable(Level.FINE))
						{
							StringBuffer message = new StringBuffer("Eating incoming cookie from ");
							message.append(host).append("\n\t").append(cookie);
							_cookieLogger.logp(Level.FINE, getName(), "touchResponseHeaders", message.toString());
						}
						
						_settings.addInJar(host);
						i.remove();
					}
					
					cookies.addAll(sessionCookies);					
					break;
				case UserSettings.COOKIE_EAT:
					cookies.clear();
					break;
			}
		}
	}

	
	/**
	 * Returns <tt>true</tt> if the Transfer-Encoding
	 * is "chunked", "identity", or doesn't exist.
	 */
	protected boolean hasKnownTransferCoding(Message message)
	{
		String transferCoding = message.lastTransferCoding();
		if(
			transferCoding == null ||
			transferCoding.equalsIgnoreCase("chunked") ||
			transferCoding.equalsIgnoreCase("identity")
		)
			return true;
		else
			return false;
	}
	

	protected void sendResponseBody() throws IOException
	{
		if(_res.isBodyless() || _req.getMethod().equals("HEAD"))
			return;

		String transferCoding = _res.lastTransferCoding();
		if(transferCoding != null)
		{
			if(transferCoding.equalsIgnoreCase("chunked"))
			{
				copyChunks(_server.in, _client.out);
				_res.getTrailer().read(_server.in);
				_res.getTrailer().write(_client.out);
			}
			else
			{
				StringBuffer message = new StringBuffer();
				message.append(_settings.uiHandler().getResource("err.servercoding"));
				message.append(": ").append(transferCoding);
				
				_logger.logp(Level.WARNING, getName(), "sendReponseBody", message.toString());
				
				// ugly, but we can't send another response at this point
				throw new IOException();
			}
		}
		else if(_resHeaders.contains("Content-Length"))
		{
			try
			{
				copyStream(_server.in, _client.out, Integer.parseInt(_resHeaders.getValue("Content-Length")));
			}
			catch(NumberFormatException e)
			{
				StringBuffer message = new StringBuffer();
				message.append(_settings.uiHandler().getResource("err.badcontentlength"));
				message.append(": ").append(_resHeaders.getValue("Content-Length"));
				
				_logger.logp(Level.WARNING, getName(), "sendReponseBody", message.toString());
				
				// ugly, but we can't send another response at this point
				throw new IOException();
			}
		}
		else
		{
			// body delimited by closing connection
			copyStream(_server.in, _client.out, -1);
			_client.setKeepConnection(false);
		}
		
		_server.setTimestamp();
	}
	
	
	protected void copyChunks(InputStream in, OutputStream out) throws IOException
	{
		ChunkedInputStream  cin  = new ChunkedInputStream(in);
		ChunkedOutputStream cout = new ChunkedOutputStream(out);
		
		byte[] buffer = new byte[4096];
		int read = 0;
		
		cin.startChunk();
		while(cin.chunkSize() > 0)
		{
			cout.startChunk(cin.chunkSize(), cin.extensions());
			while(cin.chunkLeft() > 0)
			{
				read = cin.read(buffer);
				if(read == -1)
					throw new IOException("Unexpected end of stream.");
				cout.write(buffer, 0, read);
			}
			cout.endChunk();
			cin.startChunk();
		}
		cout.close();
		cin.close();
	}
	
	public String getName() {
		return _name;
	}
	
}

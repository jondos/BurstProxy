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
import java.net.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

import myproxy.filter.*;
import myproxy.httpio.*;
import myproxy.prefetching.PrefetchedEntityStore;

/**
 * Processes one request at a time, as long
 * as the client connection is alive.
 */
public final class Handler implements Runnable
{
	private static final int SERVER_COMM_TIMEOUT = 2 * 60 * 1000;
	private static final int SERVER_IDLE_TIMEOUT = 15 * 1000;
	
	private static final Logger _logger = Logger.getLogger("myproxy.handler");

	private static int _nextID = 0;
	private static boolean _shutdown = false;
	
	private final String _name;
	private final MyProxy _controller;
	private final Map _servers;
	private final Request _req;
	private final Response _res;
	private final URIParser _uri;
	private final String _forwardHostKey;
	private Headers _reqHeaders, _resHeaders;

	private Connection _client, _server;
	private UserSettings _settings;
	
	private RequestHandler _requestHandler;
	private String _localURL;
	private String _gifURL;
	
	
	public Handler(MyProxy controller)
	{
		_name = "Handler" + nextID();
		_controller = controller;
		_servers = new HashMap();
		_req = new Request();
		_res = new Response();
		_reqHeaders = _req.getHeaders();
		_resHeaders = _res.getHeaders();
		_uri = _req.getURI();
				
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
	
	public void run()
	{
		while(_client.keepConnection() && !doShutdown())
		{
			handleRequest();
			weedServers();
		}
		cleanUp();
		_controller.handlerFinished(this);
		_logger.finer(getName() + " finished");
	}

	void requestShutdown()
	{
		synchronized(getClass())
		{
			_shutdown = true;
		}
		_controller.requestShutdown();
	}
	
	private static synchronized boolean doShutdown()
	{
		return _shutdown;
	}

	void handleClient(Socket clientSocket) throws IOException
	{
		_client = new Connection(clientSocket);
		new Thread(this, getName()).start();
	}
	
	/** 
	 * handles a request which comes directly from the client
	 */
	private void handleRequest()
	{
		//_logger.entering(getName(), "handleRequest");
		_settings = _controller.getSettings("default");
		
		try
		{
			int oldTimeout = -1;
			try
			{
				oldTimeout = _client.getTimeout();
				_client.setTimeout(5 * 1000);
				_client.read(_req);
			}
			catch(SocketTimeoutException e)
			{
				// return to weed servers
				if(oldTimeout>=0) _client.setTimeout(oldTimeout);
				//_logger.exiting(getName(), "handleRequest");
				return;
			}
			catch(IOException e)
			{
				_client.setKeepConnection(false);
				if(oldTimeout>=0) _client.setTimeout(oldTimeout);
				return;
			}
			if(oldTimeout>=0) _client.setTimeout(oldTimeout);
			
			// see if we're handling this at all
			checkRequest();
			
			String prefetchHeader = _reqHeaders.getValue("X-Accept-Prefetching");
			String prefetchValue = "";
			if(prefetchHeader!=null) {
				String [] prefetchHeaderArray = prefetchHeader.split("\\s|,");
				prefetchValue=prefetchHeaderArray[0];
			}

			if((_controller.getSettings("default").getInteger("prefetching.value",
					UserSettings.PREFETCHING_DISABLED) == UserSettings.PREFETCHING_REMOTEEND) &&
					prefetchValue.equals("")) {
				throw new HTTPException("500", "err.remoteendheadermissing", "This proxy can only be used via a prefetching local end", true);
			}

			if(_req.getMethod().equals("CONNECT"))
			{
				_logger.logp(Level.INFO, getName(), "handleRequest", "Tunneling request for: " + _uri.getSource());
				_requestHandler = new ConnectRequestHandler(_controller, this);
			} else {
				if(isLocalRequest(getOriginServer(), getOriginPort())) {
					_requestHandler = new LocalRequestHandler(_controller, this);
				}
				else {
					if(_controller.getPrefetchingRemoteAddress()!=null) {
						if(!(_requestHandler instanceof LocalPrefetchRequestHandler)) {
							_requestHandler = new LocalPrefetchRequestHandler(_controller, this);
						} else {
							_requestHandler.reuseHandler(_controller, this);
						}
					} else if(prefetchValue!=null &&
							_controller.getSupportedPrefetchStrategies().contains(prefetchValue)) {
						if(!(_requestHandler instanceof RemotePrefetchRequestHandler)) {
							_requestHandler = new RemotePrefetchRequestHandler(_controller, this);
						} else {
							_requestHandler.reuseHandler(_controller, this);
						}
					} else {
						if(!(_requestHandler instanceof RegularRequestHandler)) {
							_requestHandler = new RegularRequestHandler(_controller, this);
						} else {
							_requestHandler.reuseHandler(_controller, this);
						}
						
					}
				}
			}
			_requestHandler.handleRequest();

		}
		catch(HTTPException e)
		{
			try
			{
				sendResponse(e);
			}
			catch(IOException ex)
			{
				// ignore
			}
			
			if(e.isFatal())
				_client.setKeepConnection(false);
		}
		catch(MessageFormatException e)
		{
			_logger.logp(Level.WARNING, getName(), "handleRequest", e.getMessage());
			
			try
			{
				sendResponse("400", "Bad Request", e.getMessage());
			}
			catch(IOException ex)
			{
				// ignore
			}
			
			_client.setKeepConnection(false);
		}
		catch(IOException e)
		{
			_logger.log(Level.WARNING, "IOException in handler: "+e.getMessage());
			e.printStackTrace();
			_client.setKeepConnection(false);
		}

		//_logger.exiting(getName(), "handleRequest");
	}
	
	public Connection establishServerConnection() throws IOException, HTTPException {
		String hostname;
		int port;
		
		// TODO: forwarding not tested yet
		if(_forwardHostKey == null)
		{
			hostname = getOriginServer();
			port = getOriginPort();
			
			StringBuffer tmp = new StringBuffer(hostname);
			tmp.append(':').append(port);
		}
		else
		{
			hostname = _controller.getForwardAddress().getHostName();
			port = _controller.getForwardAddress().getPort();
		}
		

		try
		{
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
		
		return _server;
	}
	
	private void checkRequest() throws IOException, HTTPException
	{
		if(_req.compareVersion(1, 1) < 0)
			throw new HTTPException("400", "err.protocolversion", null, true);
		
		if(!_reqHeaders.contains("Host"))
			throw new HTTPException("400", "err.hostheader", null, true);

		if(!hasKnownTransferCoding(_req))
		{
			_logger.logp(Level.WARNING, getName(), "checkRequest", "Unknown \"Transfer-Encoding\" from client: " + _req.lastTransferCoding());
			throw new HTTPException("400", "err.clientcoding", _req.lastTransferCoding(), true);
		}

		if(
			!_req.getMethod().equals("CONNECT") &&
			 _uri.getScheme() != null &&
			!_uri.getScheme().equals("http")
		)
			throw new HTTPException("501", "err.reqscheme", _uri.getScheme(), true);
	}

	/**
	 * Sends an HTTP response to the client,
	 * containing a type <tt>text/html</tt> message body.
	 * 
	 * @param message can contain HTML mark-up
	 */
	private void sendResponse(String statusCode, String reason, String message) throws IOException
	{
		StringBuffer body = new StringBuffer(80);
		
		body.append("<html>\n<head>\n");
		body.append("<title>").append(statusCode).append(' ').append(reason).append("</title>\n");
		body.append("</head>\n<body>\n");
		body.append("<h1>").append(statusCode).append(' ').append(reason).append("</h1>\n");
		body.append(message);
		body.append("\n</body>\n</html>\n");
		
		byte[] bytes = body.toString().getBytes("US-ASCII");
		
		_res.clear();
		_res.setVersion(1, 1);
		_res.setStatus(statusCode, reason);
		_resHeaders.put("Date", HTTPDate.now());
		_resHeaders.put("Content-Type", "text/html");
		_resHeaders.put("Content-Length", Integer.toString(bytes.length));
		
		_client.write(_res);

		_client.out.write(bytes);
		_client.out.flush();
	}
	
	private void sendResponse(HTTPException e) throws IOException
	{
		StringBuffer message = new StringBuffer();
		
		message.append(_settings.uiHandler().getResource(e.getResourceKey()));
		if(e.getDetail() != null)
			message.append(": ").append(e.getDetail());
		else
			message.append('.');
			
		sendResponse(e.getStatusCode(), e.getReason(), message.toString());
	}
	
	/**
	 * Returns the host to connect to, the authority part
	 * in the request URL overrides the "Host" header.
	 */
	private String getOriginServer()
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
	private int getOriginPort()
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
	
	private boolean isLocalRequest(String hostname, int port) throws HTTPException
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
	
	/**
	 * Returns <tt>true</tt> if the Transfer-Encoding
	 * is "chunked", "identity", or doesn't exist.
	 */
	private boolean hasKnownTransferCoding(Message message)
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
	
	private void cleanUp()
	{
		_req.clear();
		_res.clear();
		
		_client.safeClose();
		for(Iterator i = _servers.values().iterator(); i.hasNext();)
			((Connection)i.next()).safeClose();
		_servers.clear();
		
		_client = null;
		_server = null;
		_settings = null;
	}
	
	private void weedServers()
	{
		for(Iterator i = _servers.values().iterator(); i.hasNext();)
		{
			Connection server = (Connection)i.next();
			if(!server.isClosed() && server.getIdleTime() > SERVER_IDLE_TIMEOUT)
			{
				server.safeClose();
				i.remove();
			}
			else if(server.isClosed())
			{
				i.remove();
			}
		}
	}
	
	public String getName()
	{
		return _name;
	}
	
	private static synchronized int nextID()
	{
		return _nextID++;
	}
	
	public Request getRequest() {
		return _req;
	}
	
	public Response getResponse() {
		return _res;
	}
	
	public Connection getClientConnection() {
		return _client;
	}
}

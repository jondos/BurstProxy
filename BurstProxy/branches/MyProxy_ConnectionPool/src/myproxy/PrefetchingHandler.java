package myproxy;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import myproxy.httpio.Message;
import myproxy.httpio.MessageFormatException;
import myproxy.httpio.URIFormatException;
import myproxy.httpio.URIParser;
import myproxy.prefetching.PrefetchedEntity;

public class PrefetchingHandler implements Runnable {

	private static final int SERVER_COMM_TIMEOUT = 2 * 60 * 1000;

	private static final Logger _logger = Logger.getLogger("myproxy.handler");
	private static final Logger _msgLogger = Logger.getLogger("myproxy.messages");


	private final MyProxy _controller;
	private UserSettings _settings;

	private final int _id;
	
	private final String _name;

	/** The connection to the server */
	private Connection _server;

	private PrefetchedEntity _pe;

	private String _error;

	private boolean _alreadySent;

	private String _identifier;

	private RemotePrefetchRequestHandler _parent;
	
	public PrefetchingHandler(MyProxy controller, String handlerName, PrefetchedEntity p, int id) {
		this(controller, handlerName, p, id, null);
	}

	public PrefetchingHandler(MyProxy controller, String handlerName, PrefetchedEntity p, int id, RemotePrefetchRequestHandler parent) {
		_id = id;
		_name = handlerName + "." + _id;
		_pe = p;
		_controller = controller;
		_settings = controller.getSettings("default");
		_parent = parent;
	}


	public void run() {
		_logger.finer(getName() + " startup: " +_pe.getRequest().getURI().getSource());

		try {

			getServerConnection();
			
			// keep original request URI (will be overwritten by next statements)
			URIParser baseURI = new URIParser();
			try {
				baseURI.parse(_pe.getRequest().getURI().getSource());
			} catch(URIFormatException e) {
				throw new MessageFormatException(e.getMessage());
			}
			
			// change Request-URI to "abs_path" format, unless we're forwarding
			if(_controller.getForwardAddress() == null)
				_pe.getRequest().setURI(_pe.getRequest().getURI().getPathSource());

			try
			{
				_server.write(_pe.getRequest());
			}
			catch(SocketTimeoutException e)
			{
				throw new HTTPException("504", "err.servertimeout", null, _pe.getRequest().hasBodyHeaders());
			}
			
			// TODO: have to send *request body* (for posts), if any!

			if(_msgLogger.isLoggable(Level.FINER))
				logHTTPMessage(getName() + " request header sent to server "+_server.toString(), _pe.getRequest());

			_server.read(_pe.getResponse());
			
			if(!hasKnownTransferCoding(_pe.getResponse()))
			{
				_logger.logp(Level.WARNING, getName(), "handleRegular", "Unknown \"Transfer-Encoding\" from server: " + _pe.getResponse().lastTransferCoding());
				_server.safeClose();
				throw new HTTPException("500", "err.servercoding", _pe.getResponse().lastTransferCoding(), false);
			}

			if(_msgLogger.isLoggable(Level.FINER))
				logHTTPMessage(getName() + " response headers received.", _pe.getResponse());

			// handle body
			prefetchEntityBody();
			List urlsOfEmbeddedEntities = null;

			urlsOfEmbeddedEntities = _parent.parseDocument(_pe.getResponse().getHeaders().getValue("Content-Type"), _pe, baseURI, true);
			if(urlsOfEmbeddedEntities != null) {	
				_logger.finer(getName() + " found " +urlsOfEmbeddedEntities.size() + " urls in Prefetched Entity " + _pe.getRequest().getFullURIPath());
				Iterator urlIterator = urlsOfEmbeddedEntities.iterator();
				while(urlIterator.hasNext()) {
					String uri = (String)urlIterator.next();
					if(uri.contains("logo.gif")){
						int i = 0;
					}
					try{
						_logger.finer(getName() + " prefetching in " + this.getName() + ": " + uri);
						_parent.createNewPrefetchingHandler(uri ,baseURI, uri, true);
					} catch (URIFormatException e) {
						e.printStackTrace();
					}
				}
			}
		} catch(Exception e) {
			_logger.logp(Level.WARNING, getName(), "PrefetchingHandler.run", e.toString());

			// TODO: have to handle this case somehow
			_error = e.getMessage();
			_pe.setCompleted(true);

			e.printStackTrace();
		}

		_pe.setCompleted(true);
		//TODO 
		//if(!_server.keepConnection() || _pe.getResponse().compareVersion(1, 1) < 0)
			//_server.safeClose();
		_logger.finer(getName() + " finished: " +_pe.getRequest().getURI().getSource());
	}


	public PrefetchedEntity getEntity() {
		return _pe;
	}

	
	public void prefetchEntityBody() throws IOException, HTTPException
	{
		prefetchEntityBody(_server.in);
	}

	
	public void prefetchEntityBody(InputStream in) throws IOException, HTTPException
	{
		if(_pe.getResponse().isBodyless() ||
				(_pe.getRequest()!=null && _pe.getRequest().getMethod()!=null && _pe.getRequest().getMethod().equals("HEAD")) )
			return;
		
		_logger.finest(getName() + " receiving response body");

		String transferCoding = _pe.getResponse().lastTransferCoding();
		if(transferCoding != null)
		{
			if(transferCoding.equalsIgnoreCase("chunked"))
			{
				_pe.writeChunks(in);
				_pe.getResponse().getTrailer().read(in); // do nothing with the Trailer -> not needed any more
			}
			else
			{
				StringBuffer message = new StringBuffer();
				message.append(_settings.uiHandler().getResource("err.servercoding"));
				message.append(": ").append(transferCoding);

				_logger.logp(Level.WARNING, getName(), "prefetchAnEntity", message.toString());

				// ugly, but we can't send another response at this point
				throw new IOException();
			}
		}
		else if(_pe.getResponse().getHeaders().contains("Content-Length"))
		{
			try
			{
				// stream in ByteArrayOutput Stream-Buffer schreiben
				_pe.writeStream(in, Integer.parseInt(_pe.getResponse().getHeaders().getValue("Content-Length")));
			}
			catch(NumberFormatException e)
			{
				StringBuffer message = new StringBuffer();
				message.append(_settings.uiHandler().getResource("err.badcontentlength"));
				message.append(": ").append(_pe.getResponse().getHeaders().getValue("Content-Length"));

				_logger.logp(Level.WARNING, getName(), "prefetchAnEntity", message.toString());

				// ugly, but we can't send another response at this point
				throw new IOException();
			}
		}
		else
		{
			// stream in ByteArrayOutput Stream-Buffer schreiben
			// body delimited by closing connection
			_pe.writeStream(in, -1);
			//_client.setKeepConnection(false);
		}

		_server.setTimestamp();
	}


	/**
	 * Returns the host to connect to, the authority part
	 * in the request URL overrides the "Host" header.
	 */
	private String getOriginServer()
	{
		if(_pe.getRequest().getURI().getHost() != null)
			return _pe.getRequest().getURI().getHost();

		String hostHeader = _pe.getRequest().getHeaders().getValue("Host");
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
		if(_pe.getRequest().getURI().getPort() != -1)
			return _pe.getRequest().getURI().getPort();

		String hostHeader = _pe.getRequest().getHeaders().getValue("Host");
		int pos;
		if((pos = hostHeader.indexOf(':')) != -1)
			return Integer.parseInt(hostHeader.substring(pos + 1));

		if(_pe.getRequest().getURI().getScheme().equalsIgnoreCase("https"))
			return 443;
		return 80;
	}


	private void getServerConnection() throws HTTPException, IOException
	{
		// if there is already a server connection, do not establish a new one
		if(_server!=null)
			return;

		String hostname;
		int port;

		hostname = getOriginServer();
		port = getOriginPort();

		try
		{
			// experimental, DNS problem still happens
			InetAddress address = InetAddress.getByName(hostname);
			
			_logger.finest(getName() + " establish connection to remote server at "+hostname+":"+port);

			_server = new Connection(new Socket(address, port));
			_server.setTimeout(SERVER_COMM_TIMEOUT);
		}
		catch(UnknownHostException e)
		{
			throw new HTTPException("404", "err.unknownhost", hostname, _pe.getRequest().hasBodyHeaders());
		}
		catch(NoRouteToHostException e)
		{
			throw new HTTPException("502", "err.noroute", hostname, _pe.getRequest().hasBodyHeaders());
		}
		catch(ConnectException e)
		{
			StringBuffer detail = new StringBuffer(hostname);
			if(port != 80)
				detail.append(':').append(port);
			throw new HTTPException("502", "err.hostconnect", detail.toString(), _pe.getRequest().hasBodyHeaders());
		}
		catch(PortUnreachableException e)
		{
			throw new HTTPException("502", "err.portconnect", Integer.toString(port), _pe.getRequest().hasBodyHeaders());
		}
	}


	public String getName()
	{
		return _name;
	}



	public boolean isCompleted() {
		return _pe.isCompleted();
	}


	public void setServerConnection(Connection server) {
		_server = server;
	}


	/**
	 * Unifies a log message with an HTTP message start line.
	 * If logging at FINEST, the HTTP headers will be appended.
	 */
	private void logHTTPMessage(String message, Message httpMessage)
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
		
		_msgLogger.logp(Level.FINER, getName(), "prefetchRequest", logEntry.toString());
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


	public boolean alreadySent() {
		return _alreadySent;

	}

	public void setAreadySent(boolean sent) {
		_alreadySent = sent;
	}


	public String getIdentifier() {
		return _identifier;
	}
	
	public void setIdentifier(String id){
		_identifier = id;
	}
}

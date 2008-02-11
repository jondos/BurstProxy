package myproxy;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;

import myproxy.httpio.MessageFormatException;
import myproxy.httpio.URIFormatException;
import myproxy.httpio.URIParser;


public class RegularRequestHandler extends AbstractRequestHandler implements RequestHandler {
	
	public RegularRequestHandler(MyProxy controller, Handler handler) {
		super(controller, handler);
	}

	
	public void handleRequest() throws IOException, HTTPException, MessageFormatException {
		if(requestIsBlocked())
		{
			if(_req.hasBodyHeaders())
				_client.setKeepConnection(false);
			return;
		}
		
		boolean expectContinue = false;
		
		// check for 100-continue
		String[] expect = _reqHeaders.getValueList("Expect");
		if(expect != null)
		{
			for(int i = 0; i < expect.length; i++)
			{
				if(expect[i].equalsIgnoreCase("100-continue"))
				{
					expectContinue = true;
				}
				else
				{
					_logger.logp(Level.WARNING, _handler.getName(), "handleRegular", "Unknown \"Expect\" extension: " + expect[i]);
					throw new HTTPException("417", "err.expectext", expect[i], _req.hasBodyHeaders());
				}
			}
		}
				
		
		getServerConnection();
		
		// adjust the Max-Forwards header, if necessary
		if(_req.getMethod().equals("OPTIONS") || _req.getMethod().equals("TRACE"))
		{
			String value = _reqHeaders.getValue("Max-Forwards");
			if(value != null)
			{
				try
				{
					_reqHeaders.put("Max-Forwards", Integer.toString(Integer.parseInt(value) - 1));
				}
				catch(NumberFormatException e)
				{
					throw new HTTPException("400", "err.maxforwards", value, _req.hasBodyHeaders());
				}
			}
		}
		
		// remove/modify request headers as necessary
		touchRequestHeaders();
		
		// keep original request URI (will be overwritten by next statements)
		URIParser baseURI = new URIParser();
		try {
			baseURI.parse(_req.getURI().getSource());
		} catch(URIFormatException e) {
			throw new MessageFormatException(e.getMessage());
		}
		
		// change Request-URI to "abs_path" format, unless we're forwarding
		if(_controller.getForwardAddress() == null)
			_req.setURI(_uri.getPathSource());
		
		try
		{
			_server.write(_req);
		}
		catch(SocketTimeoutException e)
		{
			throw new HTTPException("504", "err.servertimeout", null, _req.hasBodyHeaders());
		}

		if(_msgLogger.isLoggable(Level.FINER))
			logHTTPMessage("Request header sent to server "+_server.toString(), _req);
		
		if(expectContinue)
		{
			_server.read(_res);
	
			if(!hasKnownTransferCoding(_res))
			{
				_logger.logp(Level.WARNING, _handler.getName(), "handleRegular", "Unknown \"Transfer-Encoding\" from server: " + _res.lastTransferCoding());
				_server.safeClose();
				throw new HTTPException("500", "err.servercoding", _res.lastTransferCoding(), false);
			}
			
			// remove/modify response headers as necessary
			touchResponseHeaders();
			_client.write(_res);
			
			if(!_res.getStatusCode().equals("100"))
			{
				sendResponseBody();
				if(!_server.keepConnection())
					_server.safeClose();
				return;
			}
		}
		
		// copy request body, if any
		String transferCoding = _req.lastTransferCoding();
		if(transferCoding != null)
		{
			if(transferCoding.equalsIgnoreCase("chunked"))
			{
				copyChunks(_client.in, _server.out);
				_req.getTrailer().read(_client.in);
				_req.getTrailer().write(_server.out);
				_server.setTimestamp();
			}
			else
			{
				throw new HTTPException("400", "err.clientcoding", transferCoding, true);
			}
		}
		else if(_reqHeaders.contains("Content-Length"))
		{
			try
			{
				copyStream(_client.in, _server.out, Integer.parseInt(_reqHeaders.getValue("Content-Length")));
				_server.setTimestamp();
			}
			catch(NumberFormatException e)
			{
				throw new HTTPException("400", "err.badcontentlength", _reqHeaders.getValue("Content-Length"), true);
			}
		}
		
// 		if(_doPrefetching) {
			//_server.safeClose();
			//throw new HTTPException("501", "err.notimplemented", null, false);
			
			// TODO this might break 100 continue responses,have to check into that
			
/*			prefetchEntities(baseURI);
			
		} else {*/
			// loop over any remaining 1xx responses
			do
			{
				_server.read(_res);
		
				if(!hasKnownTransferCoding(_res))
				{
					_logger.logp(Level.WARNING, _handler.getName(), "handleRegular", "Unknown \"Transfer-Encoding\" from server: " + _res.lastTransferCoding());
					_server.safeClose();
					throw new HTTPException("500", "err.servercoding", _res.lastTransferCoding(), false);
				}
				
				// remove/modify response headers as necessary
				touchResponseHeaders();
				_client.write(_res);

				if(_msgLogger.isLoggable(Level.FINER))
					logHTTPMessage("Response header sent to client.", _res);
			}
			while(_res.getStatusCode().charAt(0) == '1');
			

			sendResponseBody();
			if(!_server.keepConnection() || _res.compareVersion(1, 1) < 0)
				_server.safeClose();
		}
	//}
}


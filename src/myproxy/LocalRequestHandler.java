/**
 * 
 */
package myproxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * @author dh
 *
 */
public class LocalRequestHandler extends AbstractRequestHandler implements RequestHandler {

	public LocalRequestHandler(MyProxy controller, Handler handler) {
		super(controller, handler);
	}

	/**
	 * @see myproxy.RequestHandler#handleRequest(myproxy.Connection)
	 */
	public void handleRequest() throws IOException,
			HTTPException {
		if(_msgLogger.isLoggable(Level.FINER))
			logHTTPMessage("Local request received.", _req);

		if(_req.getMethod().equals("GET") || _req.getMethod().equals("HEAD"))
		{
			byte[] body = null;
			
			// check if a local resource was requested
			if(_uri.getPath().startsWith("/res/"))
			{
				String name = _uri.getPath().substring(5);
				body = _controller.getResource(name);
				
				if(body == null)
					throw new HTTPException("404", "err.localresource", name, _req.hasBodyHeaders());

				_res.clear();
				_res.setVersion(1, 1);
				_res.setStatus("200", "OK");
				_resHeaders.put("Date", HTTPDate.now());		

				if(name.endsWith(".css"))
					_resHeaders.put("Content-Type", "text/css");
				else if(name.endsWith(".gif"))
					_resHeaders.put("Content-Type", "image/gif");
				else
					_resHeaders.put("Content-Type", "application/octet-stream");

				_resHeaders.put("Content-Length", Integer.toString(body.length));
				_resHeaders.put("Cache-Control", "max-age=43200");
			}
			else
			{
				body = _settings.uiHandler().handleGet(_req, _res, _handler);
			}
			
			_client.write(_res);
			if(body != null && !_req.getMethod().equals("HEAD"))
			{
				_client.out.write(body);
				_client.out.flush();
			}
		}
		else if(_req.getMethod().equals("POST"))
		{
			if(!_reqHeaders.contains("Content-Length"))
				throw new HTTPException("411", "err.nocontentlength", null, _req.hasBodyHeaders());
			
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try
			{
				copyStream(_client.in, out, Integer.parseInt(_reqHeaders.getValue("Content-Length")));
			}
			catch(NumberFormatException e)
			{
				throw new HTTPException("400", "err.badcontentlength", _reqHeaders.getValue("Content-Length"), true);
			}
			
			byte[] body = _settings.uiHandler().handlePost(_req, _res, out.toByteArray(), _handler);
			_client.write(_res);
			if(body != null)
			{
				_client.out.write(body);
				_client.out.flush();
			}
		}
		else if(_req.getMethod().equals("OPTIONS"))
		{
			// TODO: implement OPTIONS method
			throw new HTTPException("501", "err.notimplemented", null, _req.hasBodyHeaders());
		}
		else if(_req.getMethod().equals("TRACE"))
		{
			// TODO: implement TRACE method
			throw new HTTPException("501", "err.notimplemented", null, _req.hasBodyHeaders());
		}
		else
		{
			throw new HTTPException("501", "err.methodunsup", _req.getMethod(), _req.hasBodyHeaders());
		}
	}

}

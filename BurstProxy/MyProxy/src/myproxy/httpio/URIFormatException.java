/**
 * 
 */
package myproxy.httpio;

public final class URIFormatException extends Exception
{
	private static final long serialVersionUID = 1L;

	private final String _uri;
	
	public URIFormatException(String message, String uri)
	{
		super(message);
		_uri = uri;
	}
	
	/**
	 * Returns the undecoded URI.
	 */
	public String getURI()
	{
		return _uri;
	}
	
	public String getMessage()
	{
		StringBuffer message = new StringBuffer("Invalid URI format\n\t");
		message.append(super.getMessage()).append("\n\tURI: ").append(_uri);
		
		return message.toString();
	}
}
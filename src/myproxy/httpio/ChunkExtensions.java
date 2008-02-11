package myproxy.httpio;

import java.util.HashMap;
import java.util.Map;

public class ChunkExtensions {
	Map _extensions;
	
	private static final int READING_NAME=1;
	private static final int READING_NAME_FINISHED=2;
	private static final int READING_VALUE=3;
	private static final int READING_VALUE_FINISHED = 4;
	
	public ChunkExtensions(String ext)
	{
		StringBuffer name  = new StringBuffer();
		StringBuffer value = new StringBuffer();
		
		_extensions = new HashMap();
		
		int state=ChunkExtensions.READING_NAME;
		boolean quoted=false;
		for(int i=0;i<ext.length();i++) {
			char c = ext.charAt(i);
			if(state==ChunkExtensions.READING_NAME) {
				if(c=='=')
					state=ChunkExtensions.READING_NAME_FINISHED;
				else if(c==' ' || c=='\t') ;
				else
					name.append(c);
			}
			else if(state==ChunkExtensions.READING_NAME_FINISHED) {
				if(c=='=' || c==' ' || c=='\t') ;
				else if(c=='"')
					quoted=true;
				else {
					state=ChunkExtensions.READING_VALUE;
					value.append(c);
				}
			}
			else if(state==ChunkExtensions.READING_VALUE) {
				if(quoted) {
					if(c=='"')
						state=ChunkExtensions.READING_VALUE_FINISHED;
					else
						value.append(c);
				} else {
					if(c==' ' || c=='\t' || c==';')
						state=ChunkExtensions.READING_VALUE_FINISHED;
					else
						value.append(c);
				}
			}
			else if(state==ChunkExtensions.READING_VALUE_FINISHED) {
				// store previously read pair in map
				_extensions.put(name.toString(), value.toString());
				
				// re-initialize state
				quoted = false;
				name = new StringBuffer();
				value = new StringBuffer();

				state=ChunkExtensions.READING_NAME;
				
				if(c==' ' || c=='\t') ;
				else name.append(c);
			}
		}
		_extensions.put(name.toString(), value.toString());
		
	}
	
	public Map getExtensions()
	{
		return _extensions;
	}
	
	public String get(String name)
	{
		return (String)_extensions.get(name);
	}
	
	public String getType()
	{
		return get("type");
	}
	
	public int getHeaderLength() throws NumberFormatException
	{
		return Integer.parseInt(get("header-length"));
	}
	
	public String getUrl()
	{
		return get("url");
	}
	
	public String getHeaderEncoding()
	{
		return get("HE");
	}

	public String getBodyEncoding()
	{
		return get("BE");
	}
}

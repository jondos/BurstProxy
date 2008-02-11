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

/**
 * Adds only minor functionality to the
 * <code>java.util.Properties</code>.
 */
public class Settings
{
	private final Properties _props;
	
	public Settings()
	{
		_props = new Properties();
	}
	
	public void set(String key, String value)
	{
		_props.setProperty(key, value);
	}
	
	public String get(String key, String defaultValue)
	{
		return _props.getProperty(key, defaultValue);
	}
	
	public void setInteger(String key, int value)
	{
		_props.setProperty(key, Integer.toString(value));
	}
	
	public int getInteger(String key, int defaultValue)
	{
		if(_props.getProperty(key) != null)
			return Integer.parseInt(_props.getProperty(key));
		else
			return defaultValue;
	}
	
	public void read(InputStream in) throws IOException
	{
		_props.load(in);
	}
	
	public void write(OutputStream out, String header) throws IOException
	{
		_props.store(out, header);
	}
}

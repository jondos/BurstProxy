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
package myproxy.filter;

/**
 * Performs string matching against a wildcard pattern.
 * 
 * <tt>*</tt> matches any number of characters,
 * while <tt>?</tt> matches a single character.
 */
public final class WildcardMatcher
{
	private final String _pattern;

	/**
	 * @param pattern a pattern of at least one character
	 */
	public WildcardMatcher(String pattern)
	{
		if(pattern == null || pattern.length() == 0)
			throw new IllegalArgumentException("Invalid pattern.");
		
		_pattern = pattern;
	}
	
	/**
	 * @param text a non-null string
	 */
	public boolean matches(String text)
	{
		if(text == null)
 			throw new IllegalArgumentException("Invalid text.");
 		if(text.length() == 0 && _pattern.equals("*"))
 			return true;

		return match(_pattern, 0, text, 0);
	}
	
	private boolean match(String p, int pOff, String t, int tOff)
	{
		if(pOff == p.length())
			// check if we're past the text as well
			return (tOff == t.length());
		else if(tOff == t.length())
			// check if only a trailing '*' remains
			return (pOff == p.length() - 1 && p.charAt(pOff) == '*');
		
		switch(p.charAt(pOff))
		{
			case '*':
				pOff++;
				
				if(pOff == p.length())
					return true;
					
				final int minLength = minLength(p, pOff);
				for(int i = 0; i < t.length() - tOff - minLength + 1; i++)
				{
					if(match(p, pOff, t, tOff + i))
						return true;
				}
				
				return false;
			case '?':
				return match(p, pOff + 1, t, tOff + 1);
			default:
				if(p.charAt(pOff) == t.charAt(tOff))
					return match(p, pOff + 1, t, tOff + 1);
				else
					return false;
		}
	}
	
	/**
	 * Returns the number of "hard" characters
	 * in a pattern. (All that are not '*')
	 */
	private int minLength(String text, int offset)
	{
		int length = 0;
		
		final int range = text.length() - offset;
		for(int i = 0; i < range; i++)
		{
			if(text.charAt(offset + i) != '*')
				length++;
		}
		
		return length;
	}
}

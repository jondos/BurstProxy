/**
 * org.m80.util.Perl - Perl utility class
 *
 * Copyright (C) 1999 Brian Ng <brian@m80.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package org.m80.util;

/**
 * This class contains static method implementations of Perl-like functions.
 *
 * @author Brian Ng
 */
public class Perl
{
    /**
     * Given a character array, this method constructs a new string without
     * trailing carriage return or newline characters.
     *
     * @param charArray the character array to construct string from
     * @return A string without trailing carriage returns or newlines.
     */
    public static String chomp(char[] charArray)
    {
        // length of String to return
        int length = charArray.length;

        // index of last char in buffer
        int endIndex = charArray.length - 1;

        // From end of char buffer...
        for(int i = endIndex; i > -1; i--)
        {
            // ...identify carriage return or newline...
            if(charArray[i] == '\r' || charArray[i] == '\n')
            {
                // ...decrement length of String to return
                length--;
            }
            else
            {
                // ...or exit loop
                break;
            }
        }

        return new String(charArray, 0, length);
    }
}

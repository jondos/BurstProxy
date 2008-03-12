/**
 * org.m80.html.TemplateTokenizer - HTML template tokenizer class
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

package org.m80.html;

import java.util.*;

/**
 * This class breaks a source string into template elements.
 */
public class TemplateTokenizer implements Iterator
{
    private int curPos;
    private int maxPos;
    private String templateSource = null;
    private TemplateElement lastDelimiter = null;

    /**
     * Constructs a TemplateTokenizer with the specified source string.
     */
    public TemplateTokenizer(String templateSource)
    {
        curPos = 0;
        maxPos = templateSource.length();
        this.templateSource = templateSource;
    }

    /**
     * Tests if there are more template elements available from this
     * tokenizer's string. If this method returns true, then a subsequent
     * call to next will successfully return a template element.
     *
     * @return true if and only if there is at least one token in the source
     *              string after the current position; false otherwise.
     */
    public boolean hasNext()
    {
        return (curPos < maxPos);
    }

    private boolean hasLastDelimiter()
    {
        return (lastDelimiter != null);
    }

    private int eatDelimiter(int startPos)
    {
        int endPos = startPos;
        char ch = templateSource.charAt(startPos);

        if(Character.isUpperCase(ch))
        {
            while(Character.isUpperCase(ch) ||
                    ch == '_' ||
                    Character.isDigit(ch))
            {
                // Valid delimiter character
                endPos++;

                if(endPos >= maxPos)
                {
                    break;
                }

                // Validate next delimiter character
                ch = templateSource.charAt(endPos);
            }
        }

        return endPos;
    }

    private int eatToChar(int startPos, char endChar)
    {
        int endPos = startPos;
        char ch = templateSource.charAt(endPos);

        while(ch != endChar)
        {
            endPos++;

            if(ch == '\\')
            {
                // Escape
                endPos++;
            }

            if(endPos >= maxPos)
            {
                break;
            }

            ch = templateSource.charAt(endPos);
        }

        return endPos;
    }

    private int eatWhitespace(int startPos)
    {
        int endPos = startPos;
        char ch = templateSource.charAt(endPos);

        while(Character.isWhitespace(ch))
        {
            endPos++;
            ch = templateSource.charAt(endPos);
        }

        return endPos;
    }

    private IncludeElement parseIncludeElement(int startPos)
    {
        int endPos = startPos + 8;

        if(endPos >= maxPos)
        {
            return null;
        }

        if(templateSource.charAt(endPos) != '(')
        {
            // Expecting leading '('
            return null;
        }

        int pathStartPos = endPos + 1;
        int pathEndPos = eatToChar(pathStartPos, ')');

        if(pathEndPos == pathStartPos)
        {
            // Expecting at least one char
            return null;
        }

        if(pathEndPos >= maxPos)
        {
            // Expected terminating ')'
            return null;
        }

        endPos = pathEndPos + 1;
        String source = templateSource.substring(startPos, endPos);
        String path = templateSource.substring(pathStartPos, pathEndPos);
        IncludeElement element = new IncludeElement(source);

        element.setPath(path);
        return element;
    }

    private SetDirective parseSetDirective(int startPos)
    {
        int endPos = startPos + 4;

        if(endPos >= maxPos)
        {
            return null;
        }

        if(templateSource.charAt(endPos) != '(')
        {
            // Expecting leading '('
            return null;
        }

        // Find name
        int nameStartPos = endPos + 1;
        int nameEndPos = eatDelimiter(nameStartPos);

        if(nameEndPos == nameStartPos)
        {
            // Expected at least one uppercase char
            return null;
        }

        // Find delimiter
        int delimStartPos = nameEndPos;
        int delimEndPos = eatWhitespace(delimStartPos);

        if(templateSource.charAt(delimEndPos) != ',')
        {
            // Expected ','
            return null;
        }

        delimEndPos = eatWhitespace(delimEndPos + 1);

        if(templateSource.charAt(delimEndPos) != '"')
        {
            // Expected terminating '"'
            return null;
        }

        // Find value
        int valueStartPos = delimEndPos + 1;
        int valueEndPos = eatToChar(valueStartPos, '"');

        if(valueEndPos >= maxPos)
        {
            // Expected terminating '"'
            return null;
        }

        if(templateSource.charAt(valueEndPos + 1) != ')')
        {
            // Expected terminating ')'
            return null;
        }

        // Return set element
        endPos = valueEndPos + 2;
        String source = templateSource.substring(startPos, endPos);
        String name = templateSource.substring(nameStartPos, nameEndPos);
        String value = templateSource.substring(valueStartPos, valueEndPos);

        SetDirective directive = new SetDirective(source);
        directive.setName(name);
        directive.setValue(value);
        return directive;
    }

    private TemplateElement parseDelimiter(int startPos)
    {
        int endPos = eatDelimiter(startPos + 1);

        if(endPos == startPos + 1)
        {
            // Invalid delimiter
            return null;
        }

        String delimiter = templateSource.substring(startPos, endPos);

        if(delimiter.startsWith("$SET"))
        {
            TemplateElement element = parseSetDirective(startPos);

            if(element != null)
            {
                return element;
            }
        }
        else if(delimiter.startsWith("$INCLUDE"))
        {
            TemplateElement element = parseIncludeElement(startPos);

            if(element != null)
            {
                return element;
            }
        }

        String source = templateSource.substring(startPos, endPos);
        String name = templateSource.substring(startPos + 1, endPos);

        VariableElement element = new VariableElement(source);
        element.setName(name);
        return element;
    }

    private Object getLastDelimiter()
    {
        curPos += lastDelimiter.getSourceLength();
        TemplateElement delimiter = lastDelimiter;
        lastDelimiter = null;
        return delimiter;
    }

    /**
     * Returns the next template element from this tokenizer.
     *
     * @return The next template element from this tokenizer.
     * @throws NoSuchElementException if there are no more template elements
     *                                in this tokenizer's string.
     */
    public Object next() throws NoSuchElementException
    {
        if(!hasNext())
        {
            throw new NoSuchElementException();
        }

        if(hasLastDelimiter())
        {
            return getLastDelimiter();
        }

        int startPos = curPos;
        int delimStartPos = templateSource.indexOf('$', curPos);

        while(delimStartPos >= 0)
        {
            // Found delimiter candidate, validate
            TemplateElement delimiter = parseDelimiter(delimStartPos);

            if(delimiter != null)
            {
                // Valid delimiter
                if(delimStartPos == startPos)
                {
                    // Leading delimiter, no need to cache
                    curPos = delimStartPos + delimiter.getSourceLength();
                    return delimiter;
                }
                else
                {
                    // Trailing delimiter, cache
                    lastDelimiter = delimiter;
                    curPos = delimStartPos;

                    // Return everything up to delimiter
                    return new StringElement(templateSource.substring(startPos,
                            delimStartPos));
                }
            }

            // Invalid delimiter, find next possible delimiter
            delimStartPos = templateSource.indexOf('$', delimStartPos + 1);
        }

        // No delimiter candidates found, return rest of source string
        curPos = maxPos;
        return new StringElement(templateSource.substring(startPos, maxPos));
    }

    /**
     * Not supported by this Iterator.
     */
    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}

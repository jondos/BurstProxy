/**
 * org.m80.html.Template - HTML template class
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

import java.io.*;
import java.util.*;
import org.m80.util.*;

/**
 * This class represents an HTML template.
 */
public class Template
{
    private HashMap templateVars = new HashMap();
    private ArrayList templateElements = new ArrayList();

    /**
     * Constructs a Template.
     */
    public Template()
    {
        // Empty for now
    }

    /**
     * Constructs a template with the specified file content.
     */
    public Template(String filename)
    {
        this();
        indexSource(Perl.chomp(readFile(filename)));
    }

    /**
     * Clears all template variable values.
     */
    public void clear()
    {
        templateVars.clear();
    }

    /**
     * Formats exception.
     *
     * @param e the exception to format
     * @return The formatted exception string.
     */
    public String formatException(Exception e)
    {
        String message = e.getMessage();
        StringBuffer buffer = null;

        int offset = message.indexOf('(') + 1;
        int endIndex = message.lastIndexOf(')');

        buffer = new StringBuffer();
        buffer.append("[ ");
        buffer.append(message.substring(offset, endIndex));
        buffer.append(" ]");

        return buffer.toString();
    }

    /**
     * Returns value of template variable or empty string if no value
     * has been set.
     */

    /**
     * Gets value of template variable.
     *
     * @param varName the variable name
     * @return A string containing value of variable or empty string if no
     *         value exists.
     */
    public String get(String varName)
    {
        String value = null;

        if(templateVars.get(varName) == null)
        {
            value = new String("");
        }
        else
        {
            value = (String)templateVars.get(varName);
        }

        return value;
    }

    private void indexSource(String source)
    {
        TemplateTokenizer tokenizer = null;

        tokenizer = new TemplateTokenizer(source);

        while(tokenizer.hasNext())
        {
            templateElements.add(tokenizer.next());
        }
    }

    private char[] readFile(String filename)
    {
        File templateFile = new File(filename);
        int fileLength = (int)templateFile.length();
        char[] charBuffer = new char[fileLength];

        try
        {
            FileReader reader = new FileReader(templateFile);
            reader.read(charBuffer, 0, fileLength);
            reader.close();
        }
        catch(FileNotFoundException e)
        {
            charBuffer = formatException(e).toCharArray();
        }
        catch(IOException e)
        {
            charBuffer = formatException(e).toCharArray();
        }

        return charBuffer;
    }

    /**
     * Renders template.
     *
     * @return A string containing rendered template.
     */
    public String render()
    {
        return render(null, null);
    }

    /**
     * Renders template with specified map used to lookup variable values
     * to substitute.
     *
     * @param map a map containing variable names and values to substitute
     *            during render.
     * @return A string containing rendered template.
     */
    public String render(Map map)
    {
        return render(map, null);
    }

    /**
     * Renders template with specified variable names to substitute. Any
     * template variables found in source but not present in specified
     * variable names will be substituted with an empty string.
     *
     * @param varNames array of variable names to substitute
     * @return A string containing rendered template.
     */
    public String render(String[] varNames)
    {
        return render(null, varNames);
    }

    /**
     * Renders template with specified map used to lookup variable values
     * to substitute and specified variable names to substitute. Any template
     * variables found in source but not present in specified variable names
     * will be substituted with an empty string.
     *
     * @param map a map containing variable names and values to substitute
     *            during render.
     * @param varNames array of variable names to substitute
     * @return A string containing rendered template.
     */
    public String render(Map map, String[] varNames)
    {
        if(map == null)
        {
            map = templateVars;
        }

        if(varNames != null)
        {
            for(int i = 0; i < varNames.length; i++)
            {
                map.remove(varNames[i]);
            }
        }

        TemplateElement element = null;
        StringBuffer buffer = new StringBuffer();
        for(int i = 0; i < templateElements.size(); i++)
        {
            element = (TemplateElement)templateElements.get(i);
            buffer.append(element.render(map));
        }

        return buffer.toString();
    }

    /**
     * Sets value of template variable with specified boolean value.
     *
     * @param varName the variable name
     * @param varValue the variable value
     */
    public void set(String varName, boolean varValue)
    {
        set(varName, String.valueOf(varValue));
    }

    /**
     * Sets value of template variable with specified char value.
     *
     * @param varName the variable name
     * @param varValue the variable value
     */
    public void set(String varName, char varValue)
    {
        set(varName, String.valueOf(varValue));
    }

    /**
     * Sets value of template variable with specified char array.
     *
     * @param varName the variable name
     * @param varValue the variable value
     */
    public void set(String varName, char[] varValue)
    {
        set(varName, String.valueOf(varValue));
    }

    /**
     * Sets value of template variable with specified double value.
     *
     * @param varName the variable name
     * @param varValue the variable value
     */
    public void set(String varName, double varValue)
    {
        set(varName, String.valueOf(varValue));
    }

    /**
     * Sets value of template variable with specified float value.
     *
     * @param varName the variable name
     * @param varValue the variable value
     */
    public void set(String varName, float varValue)
    {
        set(varName, String.valueOf(varValue));
    }

    /**
     * Sets value of template variable with specified int value.
     *
     * @param varName the variable name
     * @param varValue the variable value
     */
    public void set(String varName, int varValue)
    {
        set(varName, String.valueOf(varValue));
    }

    /**
     * Sets value of template variable with specified long value.
     *
     * @param varName the variable name
     * @param varValue the variable value
     */
    public void set(String varName, long varValue)
    {
        set(varName, String.valueOf(varValue));
    }

    /**
     * Sets value of template variable with specified object.
     *
     * @param varName the variable name
     * @param varValue the variable value
     */
    public void set(String varName, Object varValue)
    {
        if(varValue == null)
        {
          // Null value, quietly ignore
        }
        else
        {
          set(varName, varValue.toString());
        }
    }

    /**
     * Sets value of template variable with specified string.
     *
     * @param varName the variable name
     * @param varValue the variable value
     */
    public void set(String varName, String varValue)
    {
        if(varName == null || varValue == null)
        {
          // Null name or value, quietly ignore
        }
        else
        {
          templateVars.put(varName.toUpperCase(), varValue);
        }
    }

    /**
     * Clears template elements and sets template source.
     *
     * @param source the source string
     */
    public void setSource(String source)
    {
        templateElements.clear();
        indexSource(source);
    }
}

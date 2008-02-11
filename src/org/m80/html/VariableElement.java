/**
 * org.m80.html.VariableElement - HTML template variable element class
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
 * VariableElement is a concrete subclass of TemplateElement and represents a
 * variable in a template.
 */
public class VariableElement extends TemplateElement
{
    private String name;

    /**
     * Constructs a VariableElement with the specified source string.
     *
     * @param source the source string
     */
    public VariableElement(String source)
    {
        this.source = source;
    }

    /**
     * Gets variable name.
     *
     * @return A string containing name of template variable.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Renders variable element by looking up value in specified map.
     *
     * @param map the map containing variable value
     * @return A string containing value of template variable.
     */
    public String render(Map map)
    {
        if(map == null)
        {
            return new String();
        }

        String rendition = (String)map.get(name);

        if(rendition == null)
        {
            return new String();
        }
        else
        {
            return rendition;
        }
    }

    /**
     * Sets variable name.
     *
     * @param name the variable name
     */
    public void setName(String name)
    {
       this.name = name;
    }
}

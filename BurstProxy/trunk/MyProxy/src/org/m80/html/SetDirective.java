/**
 * org.m80.html.SetDirective - HTML template set directive class
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
 * SetDirective is a concrete subclass of TemplateElement and represents a set
 * directive in a template.
 */
public class SetDirective extends TemplateElement
{
    private String name;
    private String value;

    /**
     * Constructs a SetDirective with the specified source string.
     */
    public SetDirective(String source)
    {
        this.source = source;
    }

    /**
     * Gets variable name to set.
     *
     * @return The variable name to set.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Gets variable value to set.
     *
     * @return The variable value to set.
     */
    public String getValue()
    {
        return value;
    }

    /**
     * Sets template variable by looking up value in specified map.
     *
     * @param map the map containing variable value
     * @return An empty string.
     */
    public String render(Map map)
    {
        if(map == null)
        {
            return new String();
        }

        map.put(name, value);

        return new String();
    }

    /**
     * Sets variable name to set.
     *
     * @param name the variable name to set
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * Sets variable value to set.
     *
     * @param value the variable value to set
     */
    public void setValue(String value)
    {
        this.value = value;
    }
}

/**
 * org.m80.html.SetDirective - HTML template include element class
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
 * IncludeElement is a concrete subclass of TemplateElement and represents an
 * include directive in a template.
 */
public class IncludeElement extends TemplateElement
{
    private Template template;

    /**
     * Constructs an IncludeElement with specified source string.
     */
    public IncludeElement(String source)
    {
        this.source = source;
    }

    /**
     * Renders include element by rendering template specified with the
     * setPath method.
     *
     * @param map a map containing variable names and values
     * @return A String containing rendered template.
     */
    public String render(Map map)
    {
        if(template == null)
        {
            return new String();
        }
        else
        {
            return template.render(map);
        }
    }

    /**
     * Sets path of template file to include.
     *
     * @param path the path to template file to include
     */
    public void setPath(String path)
    {
        template = new Template(path);
    }
}

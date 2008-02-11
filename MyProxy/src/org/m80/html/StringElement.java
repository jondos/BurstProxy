/**
 * org.m80.html.StringElement - HTML template string element class
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
 * StringElement is a concrete subclass of TemplateElement and represents
 * a string in a template.
 */
public class StringElement extends TemplateElement
{
    /**
     * Constructs a StringElement with the specified source string.
     *
     * @param source the source string
     */
    public StringElement(String source)
    {
        this.source = source;
    }

    /**
     * Renders string element.
     *
     * @return The source string.
     */
    public String render()
    {
        return getSource();
    }

    /**
     * Renders string element.
     *
     * @param map ignored
     * @return The source string.
     */
    public String render(Map map)
    {
        return render();
    }
}

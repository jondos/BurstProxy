/**
 * org.m80.html.TemplateElement - HTML template element class
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
 * This is an abstract base class from which all template elements are derived.
 */
public abstract class TemplateElement
{
    /** The source string */
    protected String source = null;

    /**
     * Gets source string.
     *
     * @return The source string.
     */
    public String getSource()
    {
        return source;
    }

    /**
     * Gets length of source string.
     *
     * @return The length of source string.
     */
    public int getSourceLength()
    {
        return source.length();
    }

    /**
     * Renders template element.
     *
     * @return String rendition of template element.
     */
    public abstract String render(Map map);
}

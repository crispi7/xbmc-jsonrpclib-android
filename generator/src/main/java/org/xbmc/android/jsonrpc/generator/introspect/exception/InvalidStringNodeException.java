/*
 *      Copyright (C) 2005-2012 Team XBMC
 *      http://xbmc.org
 *
 *  This Program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2, or (at your option)
 *  any later version.
 *
 *  This Program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with XBMC Remote; see the file license.  If not, write to
 *  the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
 *  http://www.gnu.org/copyleft/gpl.html
 *
 */
package org.xbmc.android.jsonrpc.generator.introspect.exception;

/**
 * Something that was supposed to be a string, isn't.
 * 
 * @author freezy <freezy@xbmc.org>
 */
public class InvalidStringNodeException extends Exception {
	
	public InvalidStringNodeException(String message) {
		super(message);
	}
	
	public InvalidStringNodeException(String message, Throwable cause) {
		super(message, cause);
	}

	private static final long serialVersionUID = -6319216221795443107L;
}

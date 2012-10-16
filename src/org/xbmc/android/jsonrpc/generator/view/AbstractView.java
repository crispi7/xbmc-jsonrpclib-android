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
package org.xbmc.android.jsonrpc.generator.view;

import org.xbmc.android.jsonrpc.generator.model.Enum;
import org.xbmc.android.jsonrpc.generator.model.Klass;
import org.xbmc.android.jsonrpc.generator.model.Member;
import org.xbmc.android.jsonrpc.generator.model.Namespace;
import org.xbmc.android.jsonrpc.generator.model.Parameter;

/**
 * Base class for all views. Contains useful stuff.
 * <p/>
 * More concretely, methods for getting class names out of various types.
 * 
 * @author freezy <freezy@xbmc.org>
 */
public abstract class AbstractView {

	protected String getClassName(Klass klass) {
		return getClassName(klass.getNamespace(), klass);
	}

	/**
	 * Returns the Java class name based on a class object.
	 * 
	 * @param klass Given class
	 * @return Java class name
	 */
	private String getClassName(Namespace ns, Klass klass) {
		if (klass.isNative()) {
			return getNativeType(klass);
		} else if (klass.isArray()) {
			return getArrayType(ns, klass);
		} else if (klass.isInner()) {
			return getInnerType(klass.getName(), klass.getOuterType().getName());
		} else {
			return getGlobalType(klass);
		}
	}

	/**
	 * Sometimes, in declarations the class is different compared as what the
	 * class is referred to by variables.
	 * 
	 * Example: <h3>Video.Cast</h3>
	 * <ul>
	 * <li>In the declaration, it renders <tt>Cast</tt></li>
	 * <li>In a direct reference, it renders <tt>VideoModel.Cast</tt></li>
	 * <li>In a list, it renders <tt>List&lt;VideoModel.Cast&gt;</tt></li>
	 * </ul>
	 * 
	 * @param klass
	 * @return
	 */
	protected String getClassReference(Namespace ns, Klass klass) {
		final StringBuilder sb = new StringBuilder();

		final String className = getClassName(ns, klass);
		if (!klass.isNative() && !ns.equals(klass.getNamespace()) && !className.startsWith("List")) {
			sb.append(klass.getNamespace().getName());
			sb.append(".");
		}
		sb.append(className);
		return sb.toString();
	}

	/**
	 * Returns the Java enum name based on a class object.
	 * 
	 * @param e Given enum
	 * @return Java enum name
	 */
	protected String getEnumName(Enum e) {
		if (e.isInner()) {
			return getInnerType(e.getName(), e.getOuterType().getName());
		} else {
			return getGlobalEnum(e);
		}
	}

	/**
	 * Returns the Java type of a member object.
	 * 
	 * @param member Given member
	 * @return Java class name
	 */
	protected String getClassName(Namespace ns, Member member) {
		if (!member.isEnum()) {
			return getClassName(ns, member.getType());
		} else {
			return "String";
		}
	}

	// TODO interface param and member
	protected String getClassName(Namespace ns, Parameter param) {
		if (!param.isEnum()) {
			return getClassName(ns, param.getType());
		} else {
			return "String";
		}
	}

	/**
	 * Returns the Java native type based on the JSON type.
	 * 
	 * @see http://tools.ietf.org/html/draft-zyp-json-schema-03#section-5.1
	 * @param klass type
	 * @return Java native tape
	 */
	private String getNativeType(Klass klass) {
		final String typeName = klass.getName();
		if (typeName.equals("boolean")) {
			return "Boolean";
		} else if (typeName.equals("number")) {
			return "Double";
		} else if (typeName.equals("integer")) {
			return "Integer";
		} else if (typeName.equals("string")) {
			return "String";
		} else {
			throw new IllegalArgumentException("Unknown native type \"" + typeName + "\".");
		}
	}

	protected String getArrayType(Namespace ns, Klass klass) {
		return "List<" + getClassReference(ns, Klass.resolve(klass.getArrayType())) + ">";
	}

	/**
	 * Returns a Java class name based on a global class.
	 * 
	 * There is some tweaking here so class names read nicely, like
	 * "AlbumRuleFilter" instead of "FilterRuleAlbums".
	 * 
	 * @param klass
	 * @return
	 */
	private String getGlobalType(Klass klass) {

		String name = klass.getName();
		
		// Item.All -> AllItems
		if (name.equals("ItemAll")) {
			return "AllItems";
			
		// Items.Source -> SourceItem
		} else if (name.startsWith("Items")) {
			name = getPlural(name.substring(5)) + "Item";
			
		// Item.File -> FileItem
		} else if (name.startsWith("Item")) {
			name = getPlural(name.substring(4)) + "Item";
		}
		
		// Details.Album -> AlbumDetail
		if (name.startsWith("Details")) {
			name = getPlural(name.substring(7)) + "Detail";
		}

		// Filter.Rule.Albums -> AlbumRuleFilter
		if (name.startsWith("FilterRule")) {
			name = getPlural(name.substring(10)) + "FilterRule";

		// Filter.Albums -> AlbumFilter
		} else if (name.startsWith("Filter.")) {
			name = getPlural(name.substring(7)) + "Filter";
		}

		return name.replace(".", "");
	}

	private String getGlobalEnum(Enum e) {
		String name = e.getName();

		// Fields.Files -> FileFields
		if (name.startsWith("Fields.")) {
			name = getPlural(name.substring(7)) + "Fields";
		}

		// Filter.Fields.TVShows -> TVShowFilterFields
		if (name.startsWith("Filter.Fields.")) {
			name = getPlural(name.substring(14)) + "FilterFields";

		// Filter.Operators -> OperatorFilters
		} else if (name.startsWith("Filter.")) {
			name = getPlural(name.substring(7)) + "Filters";
		}

		return name.replace(".", "");
	}

	/**
	 * Returns a Java class based on a variable name.
	 * 
	 * @param type Variable name
	 * @return Java class type
	 */
	protected String getInnerType(String type, String outerType) {

		String name = getPlural(type);
		final String suffix = outerType.equals(type) ? "Value" : "";

		// capitalize first letter
		name = name.substring(0, 1).toUpperCase() + name.substring(1);

		return name + suffix;
	}

	/**
	 * Tries to convert a plural word into the singular form.
	 * 
	 * @param plural Word in plural form
	 * @return Word in singular form
	 */
	private String getPlural(String plural) {
		if (plural.endsWith("ies") && !plural.endsWith("ovies")) {
			return plural.replace("ies", "y");
		}

		if (plural.endsWith("s")) {
			return plural.substring(0, plural.length() - 1);
		}

		return plural;
	}
	
	protected String getListGetter(Klass klass) {
		return "get" + getClassName(klass) + "List";
	}
	
	protected String getIndent(int indent) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < indent; i++) {
			sb.append("\t");
		}
		return sb.toString();
	}
}

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
package org.xbmc.android.jsonrpc.generator.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.xbmc.android.jsonrpc.generator.view.module.IClassModule;
import org.xbmc.android.jsonrpc.generator.view.module.IParentModule;

/**
 * Defines a class in an agnostic way.
 * 
 * @author freezy <freezy@xbmc.org>
 */
public class JavaClass {

	private final String name;
	private final String apiType;
	private final Namespace namespace;

	private boolean isInner = false; // = !isGlobal
	private Nature nature = null;
	
	public enum Nature {
		NATIVE, MULTITYPE, ARRAY;
	}

	/**
	 * Parent class, set if property "extends" something.
	 */
	private JavaClass parentClass = null; 
	/**
	 * If this is an array, the type is set here.
	 */
	private JavaClass arrayType = null;
	/**
	 * If this is an inner class, the outer class is set here.
	 */
	private JavaClass outerType = null; // set if isInner == true.
	
	/**
	 * If true, this is just a place holder and the "real" object has yet to be
	 * fetched.
	 */
	private final boolean unresolved;
	/**
	 * In order to avoid stack overflow due to circular references, once a 
	 * class is resolved, mark it as such.
	 */
	private boolean resolved = false;

	private final List<JavaConstructor> constructors = new ArrayList<JavaConstructor>();
	private final List<JavaMember> members = new ArrayList<JavaMember>();
	private final List<JavaClass> innerTypes = new ArrayList<JavaClass>();
	private final List<JavaEnum> innerEnums = new ArrayList<JavaEnum>();

	private final Set<String> imports = new HashSet<String>();

	
	/**
	 * Contains all global classes for resolving purpose.
	 */
	private final static Map<String, JavaClass> GLOBALS = new HashMap<String, JavaClass>();

	/**
	 * New class by reference.
	 * 
	 * Only the "id" of the global type is provided. When rendering the class
	 * later, it must be resolved by using {@link #resolve(JavaClass)}.
	 * 
	 * @param apiType Name of the global type ("id" attribute under "types").
	 */
	public JavaClass(String apiType) {
		if (apiType == null) {
			throw new IllegalArgumentException("API type must not be null when creating unresolved class references.");
		}
		this.namespace = null;
		this.name = null;
		this.apiType = apiType;
		this.unresolved = true;
		this.isInner = false;
	}

	/**
	 * New class by namespace only.
	 * 
	 * This happens only for anonymous item types ("Addon.Details" ->
	 * dependencies) where there is neither a parameter name nor a member name.
	 * 
	 * @param namespace Namespace reference
	 */
	public JavaClass(Namespace namespace) {
		this(namespace, null, null);
	}

	/**
	 * New class by namespace and variable name.
	 * 
	 * Another anonymous type, but with a given variable name, retrieved from
	 * property name or parameter name. It could also be a computed name for
	 * multitypes.
	 * 
	 * @param namespace Namespace reference
	 * @param name Best guess of name (will be transformed later depending on
	 *            type)
	 */
	public JavaClass(Namespace namespace, String name) {
		this(namespace, name, null);
	}

	/**
	 * New class for global types.
	 * 
	 * A global type, as defined in introspect's "type" list. The "id" attribute
	 * corresponds to the {@link #apiType} variable.
	 * 
	 * @param namespace Namespace reference
	 * @param name Best guess of name (will be ignored later)
	 * @param apiType Name of global type
	 */
	public JavaClass(Namespace namespace, String name, String apiType) {
		this.namespace = namespace;
		this.name = name;
		this.apiType = apiType;
		this.unresolved = false;
		if (apiType != null) {
			GLOBALS.put(apiType, this);
		}
	}

	/**
	 * Returns the resolved class object if unresolved or the same instance
	 * otherwise.
	 * 
	 * If this class had only a reference to a global type, it was marked as
	 * unresolved. Later, when all global types are transformed into
	 * {@link JavaClass} objects (e.g. when rendering), the reference can be
	 * returned via this method.
	 * </p>
	 * Note that this also resolves all the sub types of the class, like the
	 * array type and the parent type.
	 * 
	 * @param klass
	 * @return
	 */
	public static JavaClass resolve(JavaClass klass) {
		
		if (klass.resolved) {
			return klass;
		}
		
		final JavaClass resolvedClass;
		
		// resolve class itself
		if (klass.isUnresolved()) {
			if (!GLOBALS.containsKey(klass.apiType)) {
				throw new RuntimeException("Trying to resolve unknown class \"" + klass.apiType + "\".");
			}
			resolvedClass = GLOBALS.get(klass.apiType);
		} else {
			resolvedClass = klass;
		}
		
		// resolve referenced classes
		resolvedClass.resolve();
		
		return resolvedClass;
	}
	
	protected void resolve() {
		resolved = true;
		
		// resolve parent class
		if (parentClass != null) {
			parentClass = resolve(this.parentClass);
		}
		
		// ..and array type
		if (arrayType != null) {
			arrayType = resolve(arrayType);
		}
		
		// inner classes 
		final ListIterator<JavaClass> iterator = innerTypes.listIterator();
		while (iterator.hasNext()) {
			iterator.set(JavaClass.resolve(iterator.next()));
		}

		// ..and members
		for (JavaMember m : members) {
			m.resolveType();
		}
		
	}

	/**
	 * Adds type to inner types and updates the reference back
	 * to this instance.
	 * 
	 * @param klass
	 */
	public void linkInnerType(JavaClass klass) {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		innerTypes.add(klass);
		klass.setOuterType(this);
	}
	
	public void linkInnerEnum(JavaEnum e) {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		innerEnums.add(e);
		e.setOuterType(this);
	}
	
	/**
	 * Retrieves imports for each module of this class.
	 */
	public void findModuleImports(Collection<IClassModule> modules, IParentModule parentModule) {
		if (isVisible()) {
			// class render modules
			for (IClassModule module : modules) {
				imports.addAll(module.getImports(this));
				for (JavaClass klass : innerTypes) {
					klass.findModuleImports(namespace.getInnerClassModules(), namespace.getInnerParentModule());
				}
			}
			// superclass render module if available
			if (parentModule != null) {
				imports.addAll(parentModule.getImports(this));
			}
		}
	}
	
	/**
	 * Returns if the class should be rendered or not.
	 * Basically native types and array of native types are not.
	 * @return
	 */
	public boolean isVisible() {
		return !(isNative() || (isArray() && !arrayType.isVisible()));
	}

	public void addConstructor(JavaConstructor c) {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		constructors.add(c);
	}

	public void addMember(JavaMember member) {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		members.add(member);
	}

	public void addImport(String i) {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		this.imports.add(i);
	}

	public boolean hasInnerTypes() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return !innerTypes.isEmpty();
	}

	public boolean hasInnerEnums() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return !innerEnums.isEmpty();
	}

	public boolean isNative() {
		return nature == Nature.NATIVE;
	}

	public void setNative() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		nature = Nature.NATIVE;
	}

	public boolean isInner() {
		return isInner;
	}

	public void setInner(boolean isInner) {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		this.isInner = isInner;
	}

	public boolean isMultiType() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return nature == Nature.MULTITYPE;
	}

	public void setMultiType() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		nature = Nature.MULTITYPE;
	}

	public boolean isArray() {
		return nature == Nature.ARRAY;
	}

	public void setArray() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		nature = Nature.ARRAY;
	}

	public JavaClass getArrayType() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return arrayType;
	}

	public boolean isGlobal() {
		return !isInner;
	}

	public void setGlobal() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		isInner = false;
	}

	public void setArrayType(JavaClass arrayType) {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		this.arrayType = arrayType;
	}

	public String getName() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return name;
	}

	public Namespace getNamespace() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return namespace;
	}

	public String getApiType() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return apiType;
	}

	public List<JavaConstructor> getConstructors() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return constructors;
	}

	public List<JavaMember> getMembers() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		// sort before return.
		Collections.sort(members, new Comparator<JavaMember>() {
			@Override
			public int compare(JavaMember o1, JavaMember o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		return members;
	}

	public List<JavaClass> getInnerTypes() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return innerTypes;
	}

	public List<JavaEnum> getInnerEnums() {
		if (unresolved) {
			throw new RuntimeException("Unresolved.");
		}
		return innerEnums;
	}

	public boolean isUnresolved() {
		return unresolved;
	}

	public JavaClass getOuterType() {
		return outerType;
	}

	public void setOuterType(JavaClass outerType) {
		this.outerType = outerType;
	}

	public JavaClass getParentClass() {
		return parentClass;
	}

	public void setParentClass(JavaClass parentClass) {
		this.parentClass = parentClass;
	}
	
	public boolean hasParentModule() {
		return getParentModule() != null;
	}
	
	public IParentModule getParentModule() {
		return isInner ? namespace.getInnerParentModule() : namespace.getParentModule();
	}
	
	public Collection<IClassModule> getClassModules() {
		return isInner ? namespace.getInnerClassModules() : namespace.getClassModules();
	}
	
	public boolean doesExtend() {
		return parentClass != null;
	}

	public Set<String> getImports() {
		final Set<String> imports = new HashSet<String>();

		imports.addAll(this.imports);
		for (JavaMember m : members) {
			if (!m.isEnum()) {
				imports.addAll(m.getType().getImports());
			}
		}
		
		for (JavaClass klass : innerTypes) {
			imports.addAll(klass.getImports());
		}
		
		if (!innerEnums.isEmpty()) {
			imports.add("java.util.HashSet");
			imports.add("java.util.Set");
			imports.add("java.util.Arrays");
		}
		return imports;
	}
	
	@Override
	public boolean equals(Object obj) {
		return apiType != null && apiType.equals(((JavaClass)obj).apiType);
	}

}
/*******************************************************************************
 * Copyright (c) 2010 - 2015 java2script.org, webuzz.im and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Source hosted at
 * https://github.com/webuzz/simpleconfig
 * 
 * Contributors:
 *   Zhou Renjian / zhourenjian@gmail.com - initial API and implementation
 *******************************************************************************/

package im.webuzz.config;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.annotations.ConfigComment;

/**
 * Configuration field filter is used to control updating static fields without
 * accident modified unwanted fields.
 * 
 * For example, if there are class with static fields which we want to keep it
 * untouchable by configuration file. Just add a filter with excluded fields for
 * given class into the Config#configurationFilters map.
 * 
 * @author zhourenjian
 *
 */
public class ConfigFieldFilter {

	@ConfigComment({
		"Modifiers are used to filter fields.",
		"If modifiers is less than or equals to 0, modifier filter is ignored.",
		"If modifiers is 1, Modifier#PUBLIC, only public fields are configurable.",
		"If modifiers is 2, Modifier#PRIVATE, only private fields are configurable.",
		"If modifiers is 4, Modifier#PROTECTED, only protected fields are configurable.",
	})
	public int modifiers;

	@ConfigComment({
		"Included fields filter.",
		"If fields are in this set, they are configurable.",
		"If fields are not in this set, ignore them.",
	})
	public Set<String> includes;
	
	@ConfigComment({
		"Excluded fields filter.",
		"If fields are not listed in this set, they are configurable.",
	})
	public Set<String> excludes;

	public ConfigFieldFilter() {
		super();
	}

	public ConfigFieldFilter(int modifiers) {
		super();
		this.modifiers = modifiers;
	}

	public ConfigFieldFilter(String[] includes) {
		super();
		this.modifiers = -1; // do not use modifiers to filter fields
		this.includes = arrayToSet(includes);
	}

	public ConfigFieldFilter(int modifiers, String[] includes) {
		super();
		this.modifiers = modifiers;
		this.includes = arrayToSet(includes);
	}

	public ConfigFieldFilter(String[] includes, String[] excludes) {
		super();
		this.modifiers = -1; // do not use modifiers to filter fields
		this.includes = arrayToSet(includes);
		this.excludes = arrayToSet(excludes);
	}

	public ConfigFieldFilter(int modifiers, String[] includes, String[] excludes) {
		super();
		this.modifiers = modifiers;
		this.includes = arrayToSet(includes);
		this.excludes = arrayToSet(excludes);
	}

	/**
	 * Check if given field is filtered/skipped or not.
	 * @param fieldName
	 * @return true, should be skipped; false, should not be skipped.
	 */
	public boolean filterName(String fieldName) {
		if (excludes != null && excludes.contains(fieldName)) return true;
		// skip fields not included in #includes
		if (includes != null && !includes.contains(fieldName)) return true;
		return false;
	}
	
	/**
	 * Check if given field is filtered/skipped or not.
	 * @param modifiers
	 * @param filterStatic
	 * @return true, should be skipped; false, should not be skipped.
	 */
	public static boolean filterModifiers(ConfigFieldFilter filter, int modifiers, boolean filterStatic) {
		int filteringModifiers = Modifier.PUBLIC;
		if (filter != null && filter.modifiers > 0) {
			filteringModifiers = filter.modifiers;
		}
		if ((filteringModifiers <= 0 ? false : (modifiers & filteringModifiers) == 0)
				|| (modifiers & Modifier.FINAL) != 0) {
			// Ignore static, final fields
			return true;
		}
		if (filterStatic) {
			if ((modifiers & Modifier.STATIC) != 0) return true;
		} else {
			// not filter static fields
			if ((modifiers & Modifier.STATIC) == 0) return true;
		}
		return false;
	}
	
	private static Set<String> arrayToSet(String[] array) {
		if (array != null && array.length > 0) {
			Set<String> resultSet = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
			for (int i = 0; i < array.length; i++) {
				String field = array[i];
				if (field != null && field.length() > 0) {
					resultSet.add(field);
				}
			}
			return resultSet;
		}
		return null;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((excludes == null) ? 0 : excludes.hashCode());
		result = prime * result + ((includes == null) ? 0 : includes.hashCode());
		result = prime * result + modifiers;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConfigFieldFilter other = (ConfigFieldFilter) obj;
		if (excludes == null) {
			if (other.excludes != null)
				return false;
		} else if (!excludes.equals(other.excludes))
			return false;
		if (includes == null) {
			if (other.includes != null)
				return false;
		} else if (!includes.equals(other.includes))
			return false;
		if (modifiers != other.modifiers)
			return false;
		return true;
	}
	
}

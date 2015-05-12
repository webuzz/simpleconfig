package im.webuzz.config;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigFieldFilter {

	public int modifiers;
	
	public Set<String> includes;
	
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

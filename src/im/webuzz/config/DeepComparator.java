package im.webuzz.config;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/*
 * To fix #equals only checking array's address not checking array's values. 
 */
public class DeepComparator {


	public static boolean arrayDeepEquals(boolean primitiveArray, Object o1, Object o2) {
		if (primitiveArray) return deepEquals0(o1, o2);
		return deepEquals((Object[]) o1, (Object[]) o2);
	}

	// Support List#equals with array comparison
	@SuppressWarnings("rawtypes")
	public static boolean listDeepEquals(List l1, List l2) {
		if (l1 == l2) return true;
		if ((l1 == null && l2 != null) || (l1 != null && l2 == null)) return false;
		if (l1.size() != l2.size()) return false; // ArrayList
	
		ListIterator e1 = l1.listIterator();
		ListIterator e2 = l2.listIterator();
		try {
			while(e1.hasNext() && e2.hasNext()) {
				Object o1 = e1.next();
				Object o2 = e2.next();
				if (o1 == null) {
					if (o2 != null) return false;
					continue;
				}
				if (o2 == null) return false;
				if (o1 instanceof List) {
					if (!listDeepEquals((List) o1, (List) o2)) return false;
				} else if (o1 instanceof Set) {
					if (!setDeepEquals((Set) o1, (Set) o2)) return false;
				} else if (o1 instanceof Map) {
					if (!mapDeepEquals((Map) o1, (Map) o2)) return false;
				} else {
					Class<?> o1Class = o1.getClass();
					if (o1Class.isArray()) {
						if (!arrayDeepEquals(o1Class.getComponentType().isPrimitive(), o1, o2))
							return false;
						continue;
					}
					if (!o1.equals(o2)) return false;
				}
			}
			return !(e1.hasNext() || e2.hasNext());
		} catch (ClassCastException e)   {
			return false;
		} catch (NullPointerException e) {
			return false;
		}
	}

	// Support Set#equals with array comparison
	@SuppressWarnings("rawtypes")
	public static boolean setDeepEquals(Set s1, Set s2) {
		if (s1 == s2) return true;
		if ((s1 == null && s2 != null) || (s1 != null && s2 == null)) return false;
		if (s1.size() != s2.size()) return false;
	
		Iterator e2 = s2.iterator();
		try {
			while (e2.hasNext()) {
				if (!setDeepContains(s1, e2.next())) return false;
			}
			return true;
		} catch (ClassCastException e)   {
			return false;
		} catch (NullPointerException e) {
			return false;
		}
	}

	@SuppressWarnings("rawtypes")
	static boolean setDeepContains(Set s, Object o) {
		Iterator e = s.iterator();
		if (o == null) {
			while (e.hasNext())
				if (e.next() == null) return true;
			return false;
		}
		if (o instanceof List) {
			while (e.hasNext())
				if (listDeepEquals((List) o, (List) e.next())) return true;
		} else if (o instanceof Set) {
			while (e.hasNext())
				if (setDeepEquals((Set) o, (Set) e.next())) return true;
		} else if (o instanceof Map) {
			while (e.hasNext())
				if (mapDeepEquals((Map) o, (Map) e.next())) return true;
		} else {
			Class<?> oClass = o.getClass();
			if (oClass.isArray()) {
				boolean isPrimitiveArray = oClass.getComponentType().isPrimitive();
				while (e.hasNext()) {
					if (isPrimitiveArray
							? deepEquals0(o, e.next())
							: deepEquals((Object[]) o, (Object[]) e.next()))
						return true;
				}
				return false;
			}
			while (e.hasNext())
				if (o.equals(e.next())) return true;
		}
		return false;
	}

	// Support Map#equals with array comparison
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static boolean mapDeepEquals(Map m1, Map m2) {
		if (m1 == m2) return true;
		if ((m1 == null && m2 != null) || (m1 != null && m2 == null)) return false;
		if (m1.size() != m2.size()) return false;
	
		Iterator<Entry> i = m1.entrySet().iterator();
		try {
			while (i.hasNext()) {
				Entry e = i.next();
				Object key = e.getKey();
				Object value = e.getValue();
				if (value == null) {
					if (!(m2.get(key) == null && m2.containsKey(key))) return false;
					continue;
				}
				if (value instanceof List) {
					if (!listDeepEquals((List) value, (List) m2.get(key))) return false;
				} else if (value instanceof Set) {
					if (!setDeepEquals((Set) value, (Set) m2.get(key))) return false;
				} else if (value instanceof Map) {
					if (!mapDeepEquals((Map) value, (Map) m2.get(key))) return false;
				} else {
					Class<?> vClass = value.getClass();
					if (vClass.isArray()) {
						if (!arrayDeepEquals(vClass.getComponentType().isPrimitive(), value, m2.get(key)))
							return false;
						continue;
					}
					if (!value.equals(m2.get(key))) return false;
				}
			}
			return true;
		} catch (ClassCastException e) {
			// maps may have different key type or value type
			return false;
		} catch (NullPointerException e) {
			// Map may not support null key or null map
			return false;
		}
	}

	private static boolean deepEquals(Object[] a1, Object[] a2) {
		if (a1 == a2) return true;
		if (a1 == null || a2==null) return false;
		int length = a1.length;
		if (a2.length != length) return false;
	
		for (int i = 0; i < length; i++) {
			Object e1 = a1[i];
			Object e2 = a2[i];
	
			if (e1 == e2) continue;
			if (e1 == null) return false;
	
			// Figure out whether the two elements are equal
			boolean eq = deepEquals0(e1, e2);
			if (!eq) return false;
		}
		return true;
	}

	@SuppressWarnings("rawtypes")
	private static boolean deepEquals0(Object e1, Object e2) {
		assert e1 != null;
		boolean eq;
		if (e1 instanceof Object[] && e2 instanceof Object[])
			eq = deepEquals ((Object[]) e1, (Object[]) e2);
		else if (e1 instanceof byte[] && e2 instanceof byte[])
			eq = Arrays.equals((byte[]) e1, (byte[]) e2);
		else if (e1 instanceof short[] && e2 instanceof short[])
			eq = Arrays.equals((short[]) e1, (short[]) e2);
		else if (e1 instanceof int[] && e2 instanceof int[])
			eq = Arrays.equals((int[]) e1, (int[]) e2);
		else if (e1 instanceof long[] && e2 instanceof long[])
			eq = Arrays.equals((long[]) e1, (long[]) e2);
		else if (e1 instanceof char[] && e2 instanceof char[])
			eq = Arrays.equals((char[]) e1, (char[]) e2);
		else if (e1 instanceof float[] && e2 instanceof float[])
			eq = Arrays.equals((float[]) e1, (float[]) e2);
		else if (e1 instanceof double[] && e2 instanceof double[])
			eq = Arrays.equals((double[]) e1, (double[]) e2);
		else if (e1 instanceof boolean[] && e2 instanceof boolean[])
			eq = Arrays.equals((boolean[]) e1, (boolean[]) e2);
		else if (e1 instanceof List && e2 instanceof List)
			eq = listDeepEquals((List) e1, (List) e2);
		else if (e1 instanceof Set && e2 instanceof Set)
			eq = setDeepEquals((Set) e1, (Set) e2);
		else if (e1 instanceof Map && e2 instanceof Map)
			eq = mapDeepEquals((Map) e1, (Map) e2);
		else
			eq = e1.equals(e2);
		return eq;
	}

}

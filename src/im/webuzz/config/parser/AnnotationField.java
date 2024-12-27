package im.webuzz.config.parser;

import java.lang.reflect.Field;
import java.util.Objects;

public class AnnotationField implements ConfigField {
	String name;
	Class<?> type;
	Object value;
	
	@Override
	public Field getField() {
		return null;
	}
	
	public Class<?> getType() {
		return type;
	}

	public Class<?> getGenericType() {
		return null;
	}
	
	public String getName() {
		return name;
	}

	public int getInt(Object owner) {
		return value == null ? 0 : ((Integer) value).intValue();
	}
	public long getLong(Object owner) {
		return value == null ? 0 : ((Long) value).longValue();
	}
	public float getFloat(Object owner) {
		return value == null ? 0 : ((Float) value).floatValue();
	}
	public double getDouble(Object owner) {
		return value == null ? 0 : ((Double) value).doubleValue();
	}
	public byte getByte(Object owner) {
		return value == null ? 0 : ((Byte) value).byteValue();
	}
	public short getShort(Object owner) {
		return value == null ? 0 : ((Short) value).shortValue();
	}
	public boolean getBoolean(Object owner) {
		return value == null ? false : ((Boolean) value).booleanValue();
	}
	public char getChar(Object owner) {
		return value == null ? 0 : ((Character) value).charValue();
	}
	public void setInt(Object owner, int v) {
		value = v;
	}
	public void setLong(Object owner, long v) {
		value = v;
	}
	public void setFloat(Object owner, float v) {
		value = v;
	}
	public void setDouble(Object owner, double v) {
		value = v;
	}
	public void setByte(Object owner, byte v) {
		value = v;
	}
	public void setShort(Object owner, short v) {
		value = v;
	}
	public void setBoolean(Object owner, boolean v) {
		value = v;
	}
	public void setChar(Object owner, char v) {
		value = v;
	}
	
	public Object get(Object owner) {
		return value;
	}
	
	public void set(Object owner, Object v) {
		value = v;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, type.getName(), value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AnnotationField other = (AnnotationField) obj;
		return Objects.equals(name, other.name) && Objects.equals(type.getName(), other.type.getName())
				&& Objects.equals(value, other.value);
	}
	
}
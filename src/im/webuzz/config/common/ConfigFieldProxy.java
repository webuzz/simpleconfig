package im.webuzz.config.common;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

public class ConfigFieldProxy implements ConfigField {

	private Field field;

	public ConfigFieldProxy(Field field) {
		super();
		this.field = field;
	}

	
	public Field getField() {
		return field;
	}

	@Override
	public Class<?> getType() {
		return field.getType();
	}

	@Override
	public Type getGenericType() {
		return field.getGenericType();
	}

	@Override
	public String getName() {
		return field.getName();
	}

	@Override
	public int getInt(Object owner) throws IllegalArgumentException, IllegalAccessException {
		return field.getInt(owner);
	}

	@Override
	public long getLong(Object owner) throws IllegalArgumentException, IllegalAccessException {
		return field.getLong(owner);
	}

	@Override
	public float getFloat(Object owner) throws IllegalArgumentException, IllegalAccessException {
		return field.getFloat(owner);
	}

	@Override
	public double getDouble(Object owner) throws IllegalArgumentException, IllegalAccessException {
		return field.getDouble(owner);
	}

	@Override
	public byte getByte(Object owner) throws IllegalArgumentException, IllegalAccessException {
		return field.getByte(owner);
	}

	@Override
	public short getShort(Object owner) throws IllegalArgumentException, IllegalAccessException {
		return field.getShort(owner);
	}

	@Override
	public boolean getBoolean(Object owner) throws IllegalArgumentException, IllegalAccessException {
		return field.getBoolean(owner);
	}

	@Override
	public char getChar(Object owner) throws IllegalArgumentException, IllegalAccessException {
		return field.getChar(owner);
	}

	@Override
	public void setInt(Object owner, int v) throws IllegalArgumentException, IllegalAccessException {
		field.setInt(owner, v);
	}

	@Override
	public void setLong(Object owner, long v) throws IllegalArgumentException, IllegalAccessException {
		field.setLong(owner, v);
	}

	@Override
	public void setFloat(Object owner, float v) throws IllegalArgumentException, IllegalAccessException {
		field.setFloat(owner, v);
	}

	@Override
	public void setDouble(Object owner, double v) throws IllegalArgumentException, IllegalAccessException {
		field.setDouble(owner, v);
	}

	@Override
	public void setByte(Object owner, byte v) throws IllegalArgumentException, IllegalAccessException {
		field.setByte(owner, v);
	}

	@Override
	public void setShort(Object owner, short v) throws IllegalArgumentException, IllegalAccessException {
		field.setShort(owner, v);
	}

	@Override
	public void setBoolean(Object owner, boolean v) throws IllegalArgumentException, IllegalAccessException {
		field.setBoolean(owner, v);
	}

	@Override
	public void setChar(Object owner, char v) throws IllegalArgumentException, IllegalAccessException {
		field.setChar(owner, v);
	}

	@Override
	public Object get(Object owner) throws IllegalArgumentException, IllegalAccessException {
		return field.get(owner);
	}

	@Override
	public void set(Object owner, Object v) throws IllegalArgumentException, IllegalAccessException {
		field.set(owner, v);
	}
	
	
}

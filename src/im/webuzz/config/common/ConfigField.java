package im.webuzz.config.common;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

public interface ConfigField {

	public Field getField();
	public Class<?> getType();
	public Type getGenericType();
	public String getName();

	public int getInt(Object owner) throws IllegalArgumentException, IllegalAccessException;
	public long getLong(Object owner) throws IllegalArgumentException, IllegalAccessException;
	public float getFloat(Object owner) throws IllegalArgumentException, IllegalAccessException;
	public double getDouble(Object owner) throws IllegalArgumentException, IllegalAccessException;
	public byte getByte(Object owner) throws IllegalArgumentException, IllegalAccessException;
	public short getShort(Object owner) throws IllegalArgumentException, IllegalAccessException;
	public boolean getBoolean(Object owner) throws IllegalArgumentException, IllegalAccessException;
	public char getChar(Object owner) throws IllegalArgumentException, IllegalAccessException;
	public void setInt(Object owner, int v) throws IllegalArgumentException, IllegalAccessException;
	public void setLong(Object owner, long v) throws IllegalArgumentException, IllegalAccessException;
	public void setFloat(Object owner, float v) throws IllegalArgumentException, IllegalAccessException;
	public void setDouble(Object owner, double v) throws IllegalArgumentException, IllegalAccessException;
	public void setByte(Object owner, byte v) throws IllegalArgumentException, IllegalAccessException;
	public void setShort(Object owner, short v) throws IllegalArgumentException, IllegalAccessException;
	public void setBoolean(Object owner, boolean v) throws IllegalArgumentException, IllegalAccessException;
	public void setChar(Object owner, char v) throws IllegalArgumentException, IllegalAccessException;
	
	public Object get(Object owner) throws IllegalArgumentException, IllegalAccessException;
	public void set(Object owner, Object v) throws IllegalArgumentException, IllegalAccessException;

}

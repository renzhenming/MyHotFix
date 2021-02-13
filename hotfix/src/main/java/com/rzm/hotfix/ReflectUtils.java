package com.rzm.hotfix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectUtils {

    public static Field getField(Object obj, String fieldName) {
        for (Class clazz = obj.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field declaredField = clazz.getDeclaredField(fieldName);
                declaredField.setAccessible(true);
                return declaredField;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        throw new NullPointerException("no such field " + fieldName + " in " + obj.getClass() + " or its super class");
    }

    public static Object getFieldObj(Object obj, String fieldName) throws Exception {
        Field field = getField(obj, fieldName);
        Object o = field.get(obj);
        return o;
    }

    public static Method getMethod(Object object, String methodName, Class... methodParameterClass) {
        for (Class<?> aClass = object.getClass(); aClass != null; aClass = aClass.getSuperclass()) {
            try {
                Method declaredMethod = aClass.getDeclaredMethod(methodName,methodParameterClass);
                declaredMethod.setAccessible(true);
                return declaredMethod;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        throw new NullPointerException("no such method " + methodName + " in " + object.getClass() + " or its super class");
    }

}

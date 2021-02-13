package com.rzm.hotfix;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MyHotFix {

    public static final String PATCH_NAME = "fix.dex";
    public static final String HELPER_NAME = "helper.dex";

    /**
     * @param application
     * @throws Exception
     */
    public static void fixApp(Application application) throws Exception {
        File patchDexFile = copyFile(application, PATCH_NAME);
        File helperDexFile = copyFile(application, HELPER_NAME);
        List<File> patchFileList = new ArrayList<>();
        if (patchDexFile.exists()) {
            patchFileList.add(patchDexFile);
        }
        if (helperDexFile.exists()) {
            patchFileList.add(helperDexFile);
        }
        ClassLoader classLoader = application.getClassLoader();
        Object dexPathListObj = ReflectUtils.getFieldObj(classLoader, "pathList");

        Field dexElementsField = ReflectUtils.getField(dexPathListObj, "dexElements");
        Object[] dexElementsObj = (Object[]) dexElementsField.get(dexPathListObj);

        Object[] patchElementsObj = new Object[0];
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<IOException> ioExceptions = new ArrayList<>();
            Method makeDexElementsMethod = ReflectUtils.getMethod(dexPathListObj, "makePathElements", List.class, File.class, List.class);
            patchElementsObj = (Object[]) makeDexElementsMethod.invoke(dexPathListObj, patchFileList, application.getCacheDir(), ioExceptions);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ArrayList<IOException> ioExceptions = new ArrayList<>();
            Method makeDexElementsMethod = ReflectUtils.getMethod(dexPathListObj, "makeDexElements", ArrayList.class, File.class, ArrayList.class);
            patchElementsObj = (Object[]) makeDexElementsMethod.invoke(dexPathListObj, patchFileList, application.getCacheDir(), ioExceptions);
        }

        Object[] newArray = (Object[]) Array.newInstance(dexElementsObj.getClass().getComponentType(), dexElementsObj.length + patchElementsObj.length);
        System.arraycopy(patchElementsObj, 0, newArray, 0, patchElementsObj.length);
        System.arraycopy(dexElementsObj, 0, newArray, patchElementsObj.length, dexElementsObj.length);

        dexElementsField.set(dexPathListObj, newArray);
    }

    private static File copyFile(Context context, String patchName) {
        File hackFile = new File(context.getExternalFilesDir(""), patchName);
        FileOutputStream fos = null;
        InputStream is = null;
        try {
            fos = new FileOutputStream(hackFile);
            is = context.getAssets().open(patchName);
            int len;
            byte[] buffer = new byte[2048];
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
        return hackFile;
    }
}

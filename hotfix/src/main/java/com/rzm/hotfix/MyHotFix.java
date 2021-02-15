package com.rzm.hotfix;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Ref;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

public class MyHotFix {

    public static final String PATCH_NAME = "fix.dex";
    public static final String HELPER_NAME = "helper.dex";

    /**
     * @param application
     * @throws Exception
     */
    public static void fixApp(Application application) throws Exception {
        List<File> patchFileList = new ArrayList<>();
        File patchDexFile = copyFile(application, PATCH_NAME);
        if (!patchDexFile.exists()) {
            return;
        }
        patchFileList.add(patchDexFile);
        File helperDexFile = copyFile(application, HELPER_NAME);
        if (helperDexFile.exists()) {
            patchFileList.add(helperDexFile);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            injectNewClassLoader(application, patchFileList);
        }
        mergePatchDexAndAppDex(application, patchFileList);
    }

    /**
     * 为什么要替换ClassLoader?见微信团队的文档
     * https://mp.weixin.qq.com/s?__biz=MzAwNDY1ODY2OQ==&mid=2649286341&idx=1&sn=054d595af6e824cbe4edd79427fc2706&scene=0#wechat_redirect
     * @param application
     * @param patchDexFileList
     */
    private static void injectNewClassLoader(Application application, List<File> patchDexFileList) {
        ClassLoader newClassLoader = createNewClassLoader(application, patchDexFileList);
        if (newClassLoader != null && application != null) {
            injectNewClassLoader(newClassLoader, application);
        }
    }

    private static void injectNewClassLoader(ClassLoader newClassLoader, Application application) {
        try {
            //1.给当前线程设置新classloader
            Thread.currentThread().setContextClassLoader(newClassLoader);

            //2.给ContextImpl设置新classloader(ContextWrapper中)
            //application继承自ContextWrapper,获取到mBase就是ContextImpl,然后从ContextImpl中找mClassLoader
            Object baseObj = ReflectUtils.getFieldObj(application, "mBase");
            Field contextImplClassLoaderField = ReflectUtils.getField(baseObj, "mClassLoader");
            contextImplClassLoaderField.set(baseObj, newClassLoader);

            //3.给ContextImpl中的mPackageInfo设置新classloader
            Object packageInfoObj = ReflectUtils.getFieldObj(baseObj, "mPackageInfo");
            Field packageInfoObjClassLoaderField = ReflectUtils.getField(packageInfoObj, "mClassLoader");
            packageInfoObjClassLoaderField.set(packageInfoObj, newClassLoader);

            //4.给Resources设置新classloader
            Resources resources = application.getResources();
            Field resourcesClassLoaderField = ReflectUtils.getField(resources, "mClassLoader");
            resourcesClassLoaderField.set(resources, newClassLoader);

            //5.给DrawableInflater设置新classloader
            Object drawableInflaterObj = ReflectUtils.getFieldObj(resources, "mDrawableInflater");
            if (drawableInflaterObj != null) {
                Field drawableInflaterClassLoaderField = ReflectUtils.getField(drawableInflaterObj, "mClassLoader");
                drawableInflaterClassLoaderField.set(drawableInflaterObj, newClassLoader);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ClassLoader createNewClassLoader(Application application, List<File> patchDexFileList) {
        if (patchDexFileList.isEmpty()) {
            return null;
        }
        //1 先把补丁包的dex拼起来
        String combinedDexPaths = null;
        StringBuilder combinedDexPathsBuilder = new StringBuilder();
        for (File file : patchDexFileList) {
            //DexPathList源码中，切割pathList的时候就是通过File.pathSeparator，所以这里这样做
            /**
             * private static List<File> splitPaths(String searchPath, boolean directoriesOnly) {
             *         List<File> result = new ArrayList<>();
             *         if (searchPath != null) {
             *             for (String path : searchPath.split(File.pathSeparator)) {
             *                 ......
             *                 result.add(new File(path));
             *             }
             *         }
             *         return result;
             *     }
             */
            ////添加:分隔符  /xx/a.dex:/xx/b.dex 注意是个":"
            combinedDexPathsBuilder.append(file.getAbsolutePath()).append(File.pathSeparator);
        }

        //2.把apk中的dex拼起来

        try {
            //d.得到BaseDexClassLoader对象
            ClassLoader baseDexClassLoaderObj = application.getClassLoader();

            //c.从BaseDexClassLoader上得到pathList
            Object dexPathListObj = ReflectUtils.getFieldObj(baseDexClassLoaderObj, "pathList");

            //b.从DexPathList上得到dexElements
            Object[] dexElements = (Object[]) ReflectUtils.getFieldObj(dexPathListObj, "dexElements");

            String packageName = application.getPackageName();
            for (Object elementObj : dexElements) {
                //a.从Element中得到dexFile（有dexFile就可以得到dex路径）
                DexFile dexFileObj = (DexFile) ReflectUtils.getFieldObj(elementObj, "dexFile");
                String dexPath = null;
                if (dexFileObj != null) {
                    dexPath = dexFileObj.getName();
                }
                if (dexPath == null || dexPath.isEmpty()) {
                    continue;
                }
                //只添加app自己的
                if (!dexPath.contains("/" + packageName)) {
                    continue;
                }
                combinedDexPathsBuilder.append(dexPath).append(File.pathSeparator);
            }
            combinedDexPaths = combinedDexPathsBuilder.toString();

            //3.获取apk中的so加载路径
            String combinedLibraryPaths = null;
            List<File> nativeLibraryDirectoriesList = (List<File>) ReflectUtils.getFieldObj(dexPathListObj, "nativeLibraryDirectories");
            StringBuilder nativeLibraryDirectoriesBuilder = new StringBuilder();

            for (File file : nativeLibraryDirectoriesList) {
                if (file == null) {
                    continue;
                }
                nativeLibraryDirectoriesBuilder.append(file.getAbsolutePath()).append(File.pathSeparator);
            }

            combinedLibraryPaths = nativeLibraryDirectoriesBuilder.toString();

            Log.d("MyHotFix", "ClassLoader.getSystemClassLoader() = " + ClassLoader.getSystemClassLoader());
            Log.d("MyHotFix", "application.getClassLoader() = " + application.getClassLoader());
            //传入的classloader必须用ClassLoader.getSystemClassLoader()，否则报错
            //return new PathClassLoader(combinedDexPaths, combinedLibraryPaths, application.getClassLoader());
            return new PathClassLoader(combinedDexPaths, combinedLibraryPaths, ClassLoader.getSystemClassLoader());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void mergePatchDexAndAppDex(Application application, @NonNull List<File> patchFileList) throws Exception {
        if (patchFileList.isEmpty()) {
            return;
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

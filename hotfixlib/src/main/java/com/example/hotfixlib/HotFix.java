package com.example.hotfixlib;

/**
 * Created by chengchao on 2016/11/1.
 */

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;


public final class HotFix {

    public final static String TAG = "HOTFIX";
    public final static String PATCH_NAME = "patch.jar";
    public final static String STUB_NAME = "stub.jar";
    public final static String DEXPATH_DEXELEMENTS = "dexElements";
    public final static String BASECLASSLOADER_PATHLIST = "pathList";
    public final static int SDK_INT = Build.VERSION.SDK_INT;
    public static String patchFilePath = null;
    public static String stubFilePath = null;


    public static boolean init(Context context){

        // May crash above N caused by hybrid compile
        if(SDK_INT >= Build.VERSION_CODES.N){
            Log.w(TAG, "Current not support on Android " + SDK_INT);
            return false;
        }

        //If stub.jar is not exist, not use hotpatch
        if(!hasStubFile(context)){
            Log.w(TAG, "The stub.jar is not exist");
            return false;
        }

        //load the stub.jar first
        Log.d(TAG, "Apply stub.jar");
        return patchInternal(context, stubFilePath);
    }

    public static boolean getPatch(Context context) {

        //If stub.jar is exist, check the path.jar
        boolean ret = AssetUtils.hasAssetPatch(context, PATCH_NAME);
        if (ret) {
            try {
                //copy fix.patch from asset tp app file folder for test
                patchFilePath = AssetUtils.copyAsset(context, PATCH_NAME, context.getFilesDir());
                Log.d(TAG, "Copy patch file from asset to " + patchFilePath);
                return true;
            } catch (Exception ex) {
                Log.d("TAG", "Copy patch failed: " + ex.getMessage());
                ex.printStackTrace();
                return false;
            }
        }
        return false;
    }

    public static void removePatch(Context context){
        File patchFile = new File(context.getFilesDir(), PATCH_NAME);
        if (patchFile.exists()) {
            patchFile.delete();
        }
    }

    public static boolean hasPatch(Context context) {

        //Check if the path exist in app local folder
        File patchFile = new File(context.getFilesDir(), PATCH_NAME);
        if (patchFile.exists()) {
            patchFilePath = patchFile.getAbsolutePath();
            return true;
        }
        return false;
    }

    public static void patch(Context context){

        Log.d(TAG, "Apply the patch");
        patchInternal(context, patchFilePath);
        testPatch(context);
    }


    private static boolean hasStubFile(Context context){

        //Check if the stub.jar exist
        File stubFile = new File(context.getFilesDir(), STUB_NAME);
        if(stubFile.exists()){
            stubFilePath = stubFile.getAbsolutePath();
            return  true;
        }

        //If not exist, copy from asset
        boolean ret = AssetUtils.hasAssetPatch(context, STUB_NAME);
        try {
            stubFilePath = AssetUtils.copyAsset(context, STUB_NAME, context.getFilesDir());
        }catch (Exception ex) {
            Log.d("TAG", "Copy patch failed: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean patchInternal(Context context, String filePath) {

        //check if the fix.patch exist in file folder
        if (filePath != null && new File(filePath).exists()) {
            Log.d(TAG, "Begin patch: " + filePath);
            try {
                injectPatch(context, filePath, context.getFilesDir().getAbsolutePath());
                Log.d(TAG, "End Patch!");
                return true;
            } catch (Exception ex) {
                Log.d(TAG, "Patch failed : " + ex.getMessage());
                ex.printStackTrace();
                return false;
            }
        }
        Log.d(TAG, "Patch failed caused by patch file no exist!");
        return false;
    }

    private static void injectPatch(Context context, String pathFile, String outFile)
            throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {

        //using the DexClassLoader to load the patch (apk/jar/dex) file,
        //VM will do dex2opt (5.0-) or dex2oat(5.0+) operate

        //Get the Element[] dexElements from DexPathList pathList which in Patch DexClassLoacer
        DexClassLoader patchDexClassLoader = new DexClassLoader(pathFile, outFile, pathFile, context.getClassLoader());
        Object patchDexPathList = getPathList(patchDexClassLoader);
        Object patchDexElements = getDexElements(patchDexPathList);
        Log.d(TAG, "Get patchDexElements");
        //testPathList(patchDexClassLoader);

        //Get the Element[] dexElements from DexPathList pathList which in APP PatchClassLoacer
        PathClassLoader appPathClassLoader = (PathClassLoader) context.getClassLoader();
        Object appDexPathList = getPathList(appPathClassLoader);
        Object appDexElements = getDexElements(appDexPathList);
        Log.d(TAG, "Get appDexElements");
        //testPathList(appPathClassLoader);


        // Combine the tow dexElements , make the DexClassLoader dexElement at first!
        Object newDexElements = combineElements(appDexElements, patchDexElements);
        Log.d(TAG, "combine app and patch Elements");

        //Set the new DexElements into app PathClassLoader
        setDexElements(appDexPathList, newDexElements);
        Log.d(TAG, "setDexElements in to appDexPathList ");
        //testPathList(appPathClassLoader);

    }

    private static void testPathList(ClassLoader clr){
        try {
            Class<?> cl = Class.forName("com.example.chengchao.hotfixexample.TestClass2", true, clr);
            Method m = cl.getMethod("show", null);
            Object ins = cl.newInstance();
            String s = (String) m.invoke(ins);
            Log.d(TAG, "Invoke method result: " + s);
        } catch (Exception ex) {
            Log.d(TAG, "Reflect exception:" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void testPatch(Context context){

        testPathList(context.getClassLoader());

        Log.d(TAG, "Test TestClass");
        try {
            Class<?> cl = Class.forName("com.example.chengchao.hotfixexample.TestClass", true, context.getClassLoader());
            Method m = cl.getMethod("show", null);
            Object ins = cl.newInstance();
            String s = (String) m.invoke(ins);
            Log.d(TAG, "Invoke method result: " + s);
        } catch (Exception ex) {
            Log.d(TAG, "Reflect exception:" + ex.getMessage());
            ex.printStackTrace();
        }

        Log.d(TAG, "Test LibraryTestClass");
        try {
            Class<?> cl2 = Class.forName("com.example.testlibrary.LibraryTestClass", true, context.getClassLoader());
            Method m2 = cl2.getMethod("function", null);
            Object ins2 = cl2.newInstance();
            String s2 = (String) m2.invoke(ins2);
            Log.d(TAG, "Invoke method result: " + s2);
        } catch (Exception ex) {
            Log.d(TAG, "Reflect exception:" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    //get  DexPathList instance pathList from BaseDexClassLoader
    private static Object getPathList(Object obj) throws ClassNotFoundException, NoSuchFieldException,
            IllegalAccessException {
        return getField(obj, BaseDexClassLoader.class, BASECLASSLOADER_PATHLIST);
    }

    //get Element[] dexElements from DexPathList
    private static Object getDexElements(Object obj) throws NoSuchFieldException, IllegalAccessException {
        return getField(obj, obj.getClass(), DEXPATH_DEXELEMENTS);
    }

    //get Element[] dexElements into DexPathList
    private static void setDexElements(Object obj, Object value) throws NoSuchFieldException, IllegalAccessException {
        setField(obj, obj.getClass(), DEXPATH_DEXELEMENTS, value);
    }


    //Refection method
    private static Object getField(Object obj, Class className, String fieldName)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {

        Field declaredField = className.getDeclaredField(fieldName);
        declaredField.setAccessible(true);
        return declaredField.get(obj);
    }

    private static void setField(Object obj, Class className, String fieldName, Object value)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {

        Field declaredField = className.getDeclaredField(fieldName);
        declaredField.setAccessible(true);
        declaredField.set(obj, value);
    }

    //Array operate
    private static Object combineElements(Object appElement, Object patchElement) {
        Class componentType = patchElement.getClass().getComponentType();
        int length = Array.getLength(patchElement);
        int length2 = Array.getLength(appElement) + length;
        Log.d(TAG, "patchElement length=" + String.valueOf(length) + ", appElement length=" + String.valueOf(length2 - length));

        Object newInstance = Array.newInstance(componentType, length2);
        for (int i = 0; i < length2; i++) {
            if (i < length) {
                Array.set(newInstance, i, Array.get(patchElement, i));
            } else {
                Array.set(newInstance, i, Array.get(appElement, i - length));
            }
        }
        Log.d(TAG, "newElements length=" + String.valueOf(Array.getLength(newInstance)));
        return newInstance;
    }

    private static Object appendArray(Object obj, Object obj2) {
        Class componentType = obj.getClass().getComponentType();
        int length = Array.getLength(obj);
        Object newInstance = Array.newInstance(componentType, length + 1);
        Array.set(newInstance, 0, obj2);
        for (int i = 1; i < length + 1; i++) {
            Array.set(newInstance, i, Array.get(obj, i - 1));
        }
        return newInstance;
    }


    // For yunos
    /**
     private static boolean hasLexClassLoader() {
     try {
     Class.forName("dalvik.system.LexClassLoader");
     return true;
     } catch (ClassNotFoundException e) {
     return false;
     }
     }

     private static void injectInAliyunOs(Context context, String patchDexFile, String patchClassName)
     throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException,
     InstantiationException, NoSuchFieldException {
     PathClassLoader obj = (PathClassLoader) context.getClassLoader();
     String replaceAll = new File(patchDexFile).getName().replaceAll("\\.[a-zA-Z0-9]+", ".lex");
     Class cls = Class.forName("dalvik.system.LexClassLoader");
     Object newInstance =
     cls.getConstructor(new Class[] {String.class, String.class, String.class, ClassLoader.class}).newInstance(
     new Object[] {context.getDir("dex", 0).getAbsolutePath() + File.separator + replaceAll,
     context.getDir("dex", 0).getAbsolutePath(), patchDexFile, obj});
     cls.getMethod("loadClass", new Class[] {String.class}).invoke(newInstance, new Object[] {patchClassName});
     setField(obj, PathClassLoader.class, "mPaths",
     appendArray(getField(obj, PathClassLoader.class, "mPaths"), getField(newInstance, cls, "mRawDexPath")));
     setField(obj, PathClassLoader.class, "mFiles",
     combineArray(getField(obj, PathClassLoader.class, "mFiles"), getField(newInstance, cls, "mFiles")));
     setField(obj, PathClassLoader.class, "mZips",
     combineArray(getField(obj, PathClassLoader.class, "mZips"), getField(newInstance, cls, "mZips")));
     setField(obj, PathClassLoader.class, "mLexs",
     combineArray(getField(obj, PathClassLoader.class, "mLexs"), getField(newInstance, cls, "mDexs")));
     }*/
}
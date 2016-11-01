package com.example.hotfixlib;

/**
 * Created by chengchao on 2016/11/1.
 *
 *
 * */

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;

import android.content.Context;
import android.util.Log;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;


public final class HotFix {

    public  final  static  String TAG = "HOTFIX";
    public  final  static  String PATCH_NAME = "fix.patch";
    public  final  static  String DEXPATH_DEXELEMENTS = "dexElements";
    public  final  static  String BASECLASSLOADER_PATHLIST = "pathList";

    public  static  String patchFilePath = null;


    public static boolean getPatch(Context context){
        boolean ret = AssetUtils.hasAssetPatch(context, PATCH_NAME);
        if (ret){
            try {
                //copy fix.patch from asset tp app file folder
                patchFilePath =  AssetUtils.copyAsset(context, PATCH_NAME, context.getFilesDir());
                Log.d(TAG, "Copy patch file from asset to " + patchFilePath);
                return  true;
            } catch (Exception ex){
                return  false;
            }
        }
        return  false;
    }

    public static  boolean hasPatch(Context context){

        //Check if the path exist in app local folder
        File patchFile = new File(context.getFilesDir(), PATCH_NAME);
        if(patchFile.exists()){
            patchFilePath = patchFile.getAbsolutePath();
            return true;
        }
        return false;
    }

    public static void patch(Context context) {

        //check if the fix.patch exist in file folder
        if (patchFilePath != null && new File(patchFilePath).exists()) {
            Log.d(TAG, "Begin patch!");
            try {
                injectPatch(context, patchFilePath,  context.getFilesDir().getAbsolutePath());
                Log.d(TAG, "End Patch!");
            }catch (Exception ex){
                Log.d(TAG, "Patch failed : " + ex.getMessage());
            }
            return;
        }
        Log.d(TAG, "Patch failed  caused by  patch file no exist!");
    }


    private static void injectPatch(Context context, String pathFile, String outFile)
            throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {

        //using the DexClassLoader to load the patch (apk/jar/dex) file,
        //VM will do dex2opt (5.0-) or dex2oat(5.0+) operate
        DexClassLoader patchDexClassLoader = new DexClassLoader(pathFile, outFile, null, context.getClassLoader());

        //Get the PathClassLoader which load the dex files from apk.
        PathClassLoader appPathClassLoader = (PathClassLoader) context.getClassLoader();

        //Get DexPathList  pathList  from those class loader
        Object appDexPathList = getPathList(appPathClassLoader);
        Object patchDexPathList = getPathList(patchDexClassLoader);

        //Get the Element[] dexElements from those class loader
        Object appDexElements = getDexElements(appDexPathList);
        Object patchDexElements = getDexElements(patchDexPathList);

        // Combine the tow dexElements , make the DexClassLoader dexElement at first!
        Object newDexElements = combineArray(patchDexElements, appDexElements);

        //Set the new DexElements into app PathClassLoader
        setDexElements(patchDexPathList,  newDexElements);
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
            throws NoSuchFieldException,IllegalArgumentException,  IllegalAccessException {

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
    private static Object combineArray(Object obj, Object obj2) {
        Class componentType = obj2.getClass().getComponentType();
        int length = Array.getLength(obj2);
        int length2 = Array.getLength(obj) + length;
        Object newInstance = Array.newInstance(componentType, length2);
        for (int i = 0; i < length2; i++) {
            if (i < length) {
                Array.set(newInstance, i, Array.get(obj2, i));
            } else {
                Array.set(newInstance, i, Array.get(obj, i - length));
            }
        }
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
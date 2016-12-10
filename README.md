# hotfix
Android hotfix demo 

最近在做Android上热修复，所以对目前常用的热修复方式进行了一些研究。这个hotfix demo是可能目前使用的最多的一种热修复方式。


#原理
Android使用Java进行开发，Java运行在VM上(Dalvik/ART，这里以ART为列)。我们编写的每一个class，在VM中都会有一个对应的mirror::class对象，这个class对象会在我们第一次使用这个类时候被加载，一下几种情况会被加载：
 1. 创建了对象的实例
 2. 调用了类的静态方法
 3. Class.fromName()
 4. ClassLoader.loadClass()
 5. JNI env->findClass（）
 6. XXXX.class
 
 不管是那一种方式，都需要找到class的定义文件，我们知道Android的编译过程.java-->.class-->.dex-->.odex(.oat)-->.apk。最终我们所有的代码都在APK文件中的classes.dex文件中。当安装APK后，dex会被转换问odex或oat文件。我们定义的class信息都保存在这些文件中。所以去这些文件里找就可以了。Java中有一个ClassLoader，就是用来帮我们加载我们需要的class，所以一个ClassLoader中会包含一个重要的信息，就是去哪个dex文件中找。
 
 Android中有两种常用的ClassLoader，PathClassLoader和DexClassLoader，他们区别是前者是用来加载系统或安装的apk中的dex，而后者用来动态加载非系统的dex。他们有相同的父类，实际的区别就是加载后dex生成的odex或oat文件存放的位置不同而已。 他们都包含一个DexPathList的对象，DexPathList中有一个dexElements数组， 就是用来记录这个ClassLoader加载了那个dex文件。
 
 所以VM加载Class过程就很好理解，VM会遍历dexElements数组中的每一个dex文件，一旦找到需要的class信息，就创建mirror::class对象。也就是说找到第一个class定义就结束了，所以利用这个特性，很容易想到一个修改APK中class的方式：
 1. 重新编写class，放到一个单独的Dex文件中
 2. 用DexClassLoader加载这个dex
 3. 把加载的dex信息插入到系统PathClassLoader的 dexElements数组的最前面。
 4. 重启程序(killProcess或exit，不是finish activity)
 
 这里可能会有几个疑问：
 1. 为什么不直接用PathClassLoader加载?
 因为系统PathClassLoader在handleBindApplication时就创建了，我们在创建一个也没用，当然你要创建一个把系统的替换了也行吧。。不过这里用Path或Dex的都无所谓，只要目的是拿到加载后dex的的信息。
 
 2. 为什么要放入到系统PathClassLoader里面，我们用DexClassLoader加载了不是可以直接loadClass后使用新的吗？ 
 确实是可以，但是不插入到系统中去就无法替换系统现有的Class，所以没有意义。（另一个角度，系统可以存在两个同名或完全相同的class对象）
 
 3. 为什么要重启APP
 因为VM之前找到class后会放到cache table中，以后使用就不会变量dexElements数组了，所以我们插入后必须重启虚拟机。
 
 
 所以原理很简单，就是利用VM加载最先找到的Class的特性，用新的class放到老的class前面，达到热修复的目的。

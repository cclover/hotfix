# hotfix
Android hotfix demo

最近在做Android上热修复，所以对目前常用的热修复方式进行了一些研究。这个hotfix demo是可能目前使用的最多的一种热修复方式。


# 原理

## Class对象创建
Android使用Java进行开发，Java运行在VM上(Dalvik/ART，这里以ART为列)。我们编写的每一个class，在VM中都会有一个对应的mirror::class对象，这个class对象会在我们第一次使用这个类时候被加载，一下几种情况会被加载：
 * 创建了对象的实例
 * 调用了类的静态方法
 * Class.fromName()
 * ClassLoader.loadClass()
 * JNI env->findClass（）
 * XXXX.class

## 查找Class定义
不管是那一种方式，都需要找到class的定义文件，我们知道Android的编译过程.java-->.class-->.dex-->.odex(.oat)-->.apk。最终我们所有的代码都在APK文件中的classes.dex文件中。当安装APK后，dex会被转换问odex或oat文件。我们定义的class信息都保存在这些文件中。所以去这些文件里找就可以了。Java中有一个ClassLoader，就是用来帮我们加载我们需要的class，所以一个ClassLoader中会包含一个重要的信息，就是去哪个dex文件中找。

## ClassLoader加载
Android中有两种常用的ClassLoader，PathClassLoader和DexClassLoader，他们区别是前者是用来加载系统或安装的apk中的dex，而后者用来动态加载非系统的dex。他们有相同的父类，实际的区别就是加载后dex生成的odex或oat文件存放的位置不同而已。 他们都包含一个DexPathList的对象，DexPathList中有一个dexElements数组， 就是用来记录这个ClassLoader加载了那个dex文件。

所以VM加载Class过程就很好理解，VM会遍历dexElements数组中的每一个dex文件，一旦找到需要的class信息，就创建mirror::class对象。也就是说找到第一个class定义就结束了，所以利用这个特性，很容易想到一个修改APK中class的方式：
 * 1. 重新编写class，放到一个单独的Dex文件中
 * 2. 用DexClassLoader加载这个dex
 * 3. 把加载的dex信息插入到系统PathClassLoader的 dexElements数组的最前面。
 * 4. 重启程序(killProcess或exit，不是finish activity)

这里可能会有几个疑问：
 * 1. 为什么不直接用PathClassLoader加载?
 因为系统PathClassLoader在handleBindApplication时就创建了，我们在创建一个也没用，当然你要创建一个把系统的替换了也行吧。。不过这里用Path或Dex的都无所谓，只要目的是拿到加载后dex的的信息。

 * 2. 为什么要放入到系统PathClassLoader里面，我们用DexClassLoader加载了不是可以直接loadClass后使用新的吗？
 确实是可以，但是不插入到系统中去就无法替换系统现有的Class，所以没有意义。（另一个角度，系统可以存在两个同名或完全相同的class对象）

 * 3. 为什么要重启APP
 因为VM之前找到class后会放到cache table中，以后使用就不会变量dexElements数组了，所以我们插入后必须重启虚拟机。


 所以原理很简单，就是利用VM加载最先找到的Class的特性，用新的class放到老的class前面，达到热修复的目的。



# 项目
Demo是自己花了一点时间写的，没有直接使用网上那些框架，因为原理很简单，但是不自己写碰到坑的话可能搞不清楚的。一开始在Android 5.0上测试没有问题。

* app： Demo的主project
* testlibrary： 一个lib，app中调用
* hotfix：完成patch dex的加载和替换


## CLASS_ISPREVERIFIED
但是在4.X机器上就会有一个CLASS_ISPREVERIFIED相关的crash。简单说是因为dalvik在opt操作的时候，会检查两个类的调用关系。比如A类中的方法直接引用了B类，并且他们在同一个dex文件中，那么A类就会被打上CLASS_ISPREVERIFIED的标记。如果我们修改了B类，导致B和A不在同一个dex中，运行时就会报错。针对这个有两种结局办法：

* 1.让每个类都引用一个独立dex中的类
* 2.Hook dalvik resolveMethod方法，运行是不进行检查

考虑到兼容性问题，没有使用hook这种方式，而且7.0开始，如果target是N的话，已经不允许hook系统的so了，虽然这个问题只有4.0才有，但hook native是一个看上去很cool的坏主意，这也是为什么Andfix并不怎么好用。而让每个类都引用一个独立的dex文件中的类要怎么解决？我们一般都是引用一个jar包如何引用一个Dex的类。看到网上是使用了javassist框架，在编译时往java字节码中插入这段引用外部dex中class的代码。所以我们还需要多做两件事：
 * 添加一个project： stubdex用来生成独立的dex文件
 * 写一个gradle脚本project： buildserc在编译过程中往每个类的构造函数中插入对stubdex项目类的引用

写到这里我突然想到，我们只是为了让我们的类都引用一个其他dex中的类，那么我们在每个类的构造函数中加入System.out.println(Log.class)这样的代码是否可行？这样好像就不需要stubdex项目了，因为Log是在android.jar中，和我们APP的不是在一个dex中？？ 这个后面试试。

所以整个项目最终可以在4.x的机器上运行。但是制作patch没有写到build脚本中去，要自己拉去编译出的class文件使用jar和dx命令进行打包。


# 其他
这个Demo并没有处理混淆，multi-dex，自动生成patch这些事情。因为后来我改用了Instant Run的原理写了一套新的热修复框架，可以实现不重启更新代码，所以这个项目只是作为学习，所以并没有去完善。和Instant Run的方式比，这个并没有优势，而其7.0的混合编译也会出现问题，兼容性也没有Instant Run好，后面会介绍Instant Run的实现，不过因为公司会用到这个，所以不会开源代码。






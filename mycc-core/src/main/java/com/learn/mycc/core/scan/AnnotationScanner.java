package com.learn.mycc.core.scan;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.exception.MyccException;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 类路径扫描器：按包路径发现所有 @Component 类，并转换为 BeanDefinition。
 * 兼容两种类路径来源：文件系统目录（开发期 / classes 目录）与 jar 包（打包后运行），
 * 通过 URL 协议（file / jar）分流处理；可递归扫描子包。
 */
public final class AnnotationScanner {

    /** 用于加载类与定位资源的类加载器；通常在构造时绑定线程上下文类加载器。 */
    private final ClassLoader classLoader;

    /**
     * @param classLoader 用于扫描与反射加载的类加载器，不允许为 null
     */
    public AnnotationScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * 扫描指定包（含子包）下所有 @Component 类并转成 BeanDefinition 列表。
     *
     * @param basePackage 根包路径（点分隔），如 com.learn.mycc
     * @return 扫描到的组件的 BeanDefinition 列表；可能为空
     * @throws com.learn.mycc.core.exception.MyccException 扫描或类加载失败时抛出
     */
    public List<BeanDefinition> scanBeanDefinitions(String basePackage) {
        return scanComponents(basePackage).stream().map(BeanDefinition::from).toList();
    }

    /**
     * 扫描指定包（含子包）下所有 @Component 类。
     * 过程：先定位该包对应的所有资源 URL（getResources 可返回同名多资源，
     * 如多个 classpath），再按协议（file / jar）分别遍历，最后过滤出带 @Component 的类。
     *
     * @param basePackage 根包路径（点分隔）
     * @return 扫描到的 @Component 类列表；可能为空
     * @throws com.learn.mycc.core.exception.MyccException 资源枚举失败时抛出
     */
    public List<Class<?>> scanComponents(String basePackage) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            // 将包名转为目录分隔符相对路径，供 classLoader.getResources 定位
            Enumeration<URL> resources = classLoader.getResources(basePackage.replace('.', '/'));
            while (resources.hasMoreElements()) {
                collect(resources.nextElement(), basePackage, classes);
            }
        } catch (IOException e) {
            throw new MyccException("扫描包失败: " + basePackage, e);
        }
        // 先收集全部 class 再统一按 @Component 过滤：与扫描来源（目录/jar）解耦过滤逻辑
        return classes.stream().filter(c -> c.isAnnotationPresent(Component.class)).toList();
    }

    /**
     * 根据 URL 协议分发扫描：file 走目录递归，jar 走 jar 条目遍历；
     * 其它协议（如 jrt）暂不支持，忽略。
     *
     * @param url         资源定位 URL
     * @param basePackage 当前所在的包路径，用于拼接全限定类名
     * @param classes     收集结果容器
     */
    private void collect(URL url, String basePackage, List<Class<?>> classes) {
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            File dir = toDirectory(url);
            if (dir != null) {
                scanDirectory(dir, basePackage, classes);
            }
        } else if ("jar".equals(protocol)) {
            scanJar(url, basePackage, classes);
        }
    }

    /**
     * 将类路径 URL 转为本地目录文件。
     *
     * @param url 目录资源的 URL
     * @return 对应目录；URL 非法时抛异常
     * @throws com.learn.mycc.core.exception.MyccException URL 无法转为 URI 时抛出
     */
    private File toDirectory(URL url) {
        try {
            URI uri = url.toURI();
            return new File(uri);
        } catch (URISyntaxException e) {
            throw new MyccException("解析类路径 URL 失败: " + url, e);
        }
    }

    /**
     * 递归扫描目录：子目录加深包路径继续递归，.class 文件则按
     * “包名 + 相对名”载入类。
     * dir.listFiles() 返回 null（目录不可读）时静默跳过，避免扫描中断。
     *
     * @param dir         当前目录
     * @param basePackage 当前目录对应的包路径
     * @param classes     收集结果容器
     */
    private void scanDirectory(File dir, String basePackage, List<Class<?>> classes) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, basePackage + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = basePackage + "." + file.getName().substring(0, file.getName().length() - ".class".length());
                classes.add(loadClass(className));
            }
        }
    }

    /**
     * 遍历 jar 包中位于 basePackage 前缀下的 .class 条目并载入，路径分隔符转回点号。
     *
     * @param url         jar 资源 URL（jar:file:...!/包路径 形式）
     * @param basePackage 根包路径，作为 jar 条目的过滤前缀
     * @param classes     收集结果容器
     * @throws com.learn.mycc.core.exception.MyccException 打开或遍历 jar 失败时抛出
     */
    private void scanJar(URL url, String basePackage, List<Class<?>> classes) {
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jarFile = connection.getJarFile()) {
                String prefix = basePackage.replace('.', '/') + "/";
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.endsWith(".class") && name.startsWith(prefix)) {
                        String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                        classes.add(loadClass(className));
                    }
                }
            }
        } catch (IOException e) {
            throw new MyccException("扫描 jar 失败: " + url, e);
        }
    }

    /**
     * 反射加载类；initialize=false 表示仅加载不触发静态初始化，避免扫描阶段副作用。
     *
     * @param className 全限定类名
     * @return 加载后的 Class
     * @throws com.learn.mycc.core.exception.MyccException 类找不到时抛出
     */
    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new MyccException("加载类失败: " + className, e);
        }
    }
}

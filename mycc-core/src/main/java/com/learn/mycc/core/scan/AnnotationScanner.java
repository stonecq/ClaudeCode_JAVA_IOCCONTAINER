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

/** 类路径扫描器：按包路径发现 @Component 类，并转换为 BeanDefinition。 */
public final class AnnotationScanner {

    private final ClassLoader classLoader;

    public AnnotationScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /** 扫描指定包（含子包）下所有 @Component 类的 BeanDefinition。 */
    public List<BeanDefinition> scanBeanDefinitions(String basePackage) {
        return scanComponents(basePackage).stream().map(BeanDefinition::from).toList();
    }

    /** 扫描指定包（含子包）下所有 @Component 类。 */
    public List<Class<?>> scanComponents(String basePackage) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(basePackage.replace('.', '/'));
            while (resources.hasMoreElements()) {
                collect(resources.nextElement(), basePackage, classes);
            }
        } catch (IOException e) {
            throw new MyccException("扫描包失败: " + basePackage, e);
        }
        return classes.stream().filter(c -> c.isAnnotationPresent(Component.class)).toList();
    }

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

    private File toDirectory(URL url) {
        try {
            URI uri = url.toURI();
            return new File(uri);
        } catch (URISyntaxException e) {
            throw new MyccException("解析类路径 URL 失败: " + url, e);
        }
    }

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

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new MyccException("加载类失败: " + className, e);
        }
    }
}

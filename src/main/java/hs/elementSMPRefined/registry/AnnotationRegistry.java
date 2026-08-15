package hs.elementSMPRefined.registry;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.element.Element;
import hs.elementSMPRefined.items.api.ElementItem;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

/**
 * Automatic registry scanner that discovers and registers elements and items
 * using reflection and annotations.
 */
public class AnnotationRegistry {
    private final ElementSMPRefined plugin;
    private final ElementRegistry elementRegistry;
    private final ItemRegistry itemRegistry;
    private final String basePackage;

    public AnnotationRegistry(ElementSMPRefined plugin, ElementRegistry elementRegistry,
                            ItemRegistry itemRegistry, String basePackage) {
        this.plugin = plugin;
        this.elementRegistry = elementRegistry;
        this.itemRegistry = itemRegistry;
        this.basePackage = basePackage;
    }

    /**
     * Scan and register all annotated elements and items
     */
    public void scanAndRegister() {
        try {
            List<Class<?>> classes = findClasses(basePackage);
            int elementsRegistered = 0;
            int itemsRegistered = 0;

            for (Class<?> clazz : classes) {
                // Check for @RegisterElement
                RegisterElement elementAnnotation = clazz.getAnnotation(RegisterElement.class);
                if (elementAnnotation != null && Element.class.isAssignableFrom(clazz)) {
                    if (registerElement(clazz, elementAnnotation)) {
                        elementsRegistered++;
                    }
                }

                // Check for @RegisterItem
                RegisterItem itemAnnotation = clazz.getAnnotation(RegisterItem.class);
                if (itemAnnotation != null && ElementItem.class.isAssignableFrom(clazz)) {
                    if (registerItem(clazz, itemAnnotation)) {
                        itemsRegistered++;
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during annotation scanning", e);
        }
    }

    private boolean registerElement(Class<?> clazz, RegisterElement annotation) {
        try {
            Constructor<?> constructor = clazz.getConstructor(ElementSMPRefined.class);
            Element element = (Element) constructor.newInstance(plugin);

            ElementRegistry.ElementData data = ElementRegistry.ElementData.builder()
                    .displayName(annotation.displayName())
                    .description(annotation.description())
                    .color(annotation.color())
                    .isBasic(annotation.isBasic())
                    .build();

            elementRegistry.register(element, data);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to register element: " + clazz.getName(), e);
            return false;
        }
    }

    private boolean registerItem(Class<?> clazz, RegisterItem annotation) {
        try {
            Constructor<?> constructor = clazz.getConstructor(ElementSMPRefined.class);
            ElementItem item = (ElementItem) constructor.newInstance(plugin);

            ItemRegistry.ItemData data = ItemRegistry.ItemData.builder()
                    .displayName(annotation.displayName())
                    .description(annotation.description())
                    .isConsumable(annotation.isConsumable())
                    .maxStackSize(annotation.maxStackSize())
                    .requiresPermission(annotation.requiresPermission())
                    .build();

            itemRegistry.register(annotation.id(), item, data);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to register item: " + clazz.getName(), e);
            return false;
        }
    }

    /**
     * Find all classes in a package
     */
    private List<Class<?>> findClasses(String packageName) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');

        Enumeration<URL> resources = plugin.getClass().getClassLoader().getResources(path);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (resource.getProtocol().equals("jar")) {
                classes.addAll(findClassesInJar(resource, packageName));
            } else {
                // For development environment, we might need to handle file system scanning
                plugin.getLogger().warning("File system scanning not implemented for: " + resource);
            }
        }

        return classes;
    }

    /**
     * Find classes in a JAR file
     */
    private List<Class<?>> findClassesInJar(URL jarUrl, String packageName) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        String jarPath = jarUrl.getPath().substring(5, jarUrl.getPath().indexOf("!")); // strip "file:"

        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            String packagePath = packageName.replace('.', '/');

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                    String className = entryName
                            .replace('/', '.')
                            .substring(0, entryName.length() - 6);

                    try {
                        Class<?> clazz = Class.forName(className);
                        classes.add(clazz);
                    } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
                        // Skip classes that can't be loaded
                        plugin.getLogger().fine("Skipping class that can't be loaded: " + className);
                    }
                }
            }
        }

        return classes;
    }
}
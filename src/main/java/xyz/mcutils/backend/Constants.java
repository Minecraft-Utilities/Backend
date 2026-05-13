package xyz.mcutils.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class Constants {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    public static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();
    public static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
}

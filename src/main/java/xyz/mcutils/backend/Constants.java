package xyz.mcutils.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

public class Constants {
    public static final Gson GSON = new GsonBuilder()
            .setDateFormat("MM-dd-yyyy HH:mm:ss")
            .setPrettyPrinting()
            .create();
    public static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
}

package xyz.mcutils.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.http.HttpClient;

public class Constants {
    public static final String REQUEST_START_TIME_ATTRIBUTE = "requestStartTime";
    public static final Gson GSON = new GsonBuilder()
            .setDateFormat("MM-dd-yyyy HH:mm:ss")
            .setPrettyPrinting()
            .create();
    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    public static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
}

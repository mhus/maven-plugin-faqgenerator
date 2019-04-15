package de.mhus.lib.faqgenerator;

import java.util.LinkedList;
import java.util.Properties;

public class Job {

    public Configuration config;
    public LinkedList<Properties> files = new LinkedList<Properties>();
    public LinkedList<Properties> topics = new LinkedList<Properties>();

    public Job(Configuration config) {
        this.config = config;
    }

    public void addTopic(Properties context, String name) {
        topics.add(context);
        context.put("_files", new LinkedList<>());
        context.put("topicName", name);
        if (!context.containsKey("topicTitle"))
            context.put("topicTitle", name);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addFile(Properties context) {
        files.add(context);
        Object topicName = context.get("topicName");
        if (topicName == null) return;
        for (Properties topic : topics) {
            if (topicName.equals(topic.get("topicName"))) {
                ((LinkedList)topic.get("_files")).add(context);
                break;
            }
        }
    }
    
}

package de.mhus.lib.faqgenerator;

import java.util.LinkedList;
import java.util.Properties;

public class Job {

    public Configuration config;
    public LinkedList<Properties> files = new LinkedList<Properties>();

    public Job(Configuration config) {
        this.config = config;
    }

}

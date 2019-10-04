package de.mhus.lib.faqgenerator;

import java.util.Properties;

public class Configuration {

    public boolean enabled;
    public String sources;
    public String template;
    public String output;
    public boolean singleFile = true;
    public String filterGroups;
    public boolean ignoreFails;
    public Properties parameters;

    
    @Override
    public String toString() {
        return sources + " -> " + output;
    }
}

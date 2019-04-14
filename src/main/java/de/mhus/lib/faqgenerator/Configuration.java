package de.mhus.lib.faqgenerator;

public class Configuration {

    public boolean enabled;
    public String sources;
    public String template;
    public String output;
    public String filterGroups;
    public boolean ignoreFails;

    
    @Override
    public String toString() {
        return sources + " -> " + output;
    }
}

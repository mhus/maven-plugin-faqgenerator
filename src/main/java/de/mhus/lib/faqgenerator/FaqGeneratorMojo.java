package de.mhus.lib.faqgenerator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Mojo(
        name = "faq-generate", 
        defaultPhase = LifecyclePhase.PROCESS_SOURCES, 
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, 
        inheritByDefault = true
    )
public class FaqGeneratorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;
    
    @Parameter
    protected String output;

    @Parameter(defaultValue="")
    protected String filterGroups;
    
    @Parameter(defaultValue="")
    protected String template = null;

    @Parameter(defaultValue="${project}/faq")
    protected String sources = null;
    
    @Parameter(defaultValue="")
    protected String configurationFile = null;
    
    @Parameter(defaultValue="false")
    protected boolean ignoreFails = false;

    @Parameter(defaultValue="")
    protected Map<String,String> parameters = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        
        // create configuration
        
        LinkedList<Configuration> config = new LinkedList<>();
        if (MString.isSet(configurationFile)) {
            File f = new File(configurationFile);
            if (!f.exists())
                throw new MojoExecutionException("Configuration file not found: " + f.getAbsolutePath());
            readConfigurationFile(f, config);
        } else {
            // create simple configuration
            createSimpleConfiguration(config);
        }
        
        System.out.println("Configuration: " + config);
        
        // execute
        
        for (Configuration c : config) {
            if (c.enabled) {
                try {
                    Job job = new Job(c);
                    doExecute(job);
                } catch (Throwable t) {
                    if (c.ignoreFails)
                        t.printStackTrace();
                    else
                    if (t instanceof MojoExecutionException)
                        throw (MojoExecutionException)t;
                    else
                        throw new MojoExecutionException("",t);
                }
            }
        }
        
    }

    private void doExecute(Job job) throws MojoExecutionException, IOException {

        // read sources
        readSources(job);
        
        // filter groups
        filterGroups(job);
        
        // sort findings
        sortFiles(job.files);
        sortTopics(job.topics);
        
        // write template
        createTemplate(job);
    }

    @SuppressWarnings("unchecked")
    private void sortTopics(LinkedList<Properties> topics) {
        topics.sort((a,b) -> {
            String aOrder = a.getProperty("order");
            if (aOrder == null) aOrder = a.getProperty("name");
            
            String bOrder = b.getProperty("order");
            if (bOrder == null) bOrder = b.getProperty("name");
            
            return aOrder.compareTo(bOrder);
          });
        
        topics.forEach(t -> sortFiles( (LinkedList<Properties>)t.get("_files") ) );
    }

    private void sortFiles(LinkedList<Properties> files) {
        if (files == null) return;
        files.sort((a,b) -> {
            String aOrder = a.getProperty("order");
            if (aOrder == null) aOrder = a.getProperty("name");
            
            String bOrder = b.getProperty("order");
            if (bOrder == null) bOrder = b.getProperty("name");
            
            return aOrder.compareTo(bOrder);
          });
    }

    private void filterGroups(Job job) {
        if (MString.isEmpty(job.config.filterGroups)) return;
        String[] filter = job.config.filterGroups.split(",");
        job.files.removeIf(i -> {
            String[] groups = i.getProperty("groups", "").split(",");
            for (String f : filter) {
                if (f.equals("*"))
                    return false;
                if (f.startsWith("!")) {
                    f = f.substring(1);
                    for (String g : groups)
                        if (f.equals(g)) return true;
                } else {
                    for (String g : groups)
                        if (f.equals(g)) return false;
                }
            }
            return true;
        });
    }

    private void createTemplate(Job job) throws MojoExecutionException, IOException {
        
        String template = job.config.template;
        
        // load template
        if (template == null) {
            try {
                template = project.getBuild().getOutputDirectory() + "/template.twig";
                InputStream is = getClass().getResourceAsStream("/template.twig");
                FileOutputStream os = new FileOutputStream(template);
                while(true) {
                    int b = is.read();
                    if (b < 0) break;
                    os.write(b);
                }
                is.close();
                os.close();
            } catch (Exception e) {
                throw new MojoExecutionException(template,e);
            }
        }

        File templateFile = new File(template);
        JtwigTemplate jtwigTemplate = JtwigTemplate.fileTemplate(templateFile);
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("files", job.files);
        parameters.put("topics", job.topics);
        parameters.put("parameters", job.config.parameters);
        JtwigModel jtwigModel = JtwigModel.newModel(parameters);
        
        FileOutputStream fos = new FileOutputStream(new File(job.config.output));
        jtwigTemplate.render(jtwigModel, fos);
        fos.close();

    }

    private void readSources(Job job) throws MojoExecutionException, IOException {
        File f = new File(job.config.sources);
        if (!f.exists())
            throw new MojoExecutionException("Sources not found for job: " + f);
        job.files.clear();
        readSources(f, job, new Properties());
    }

    private void readSources(File f, Job job, Properties context) throws IOException {
        
        if (f.isFile()) {
            if (f.getName().endsWith(".txt")) {
                Properties fileContext = readFile(f, job, context);
                job.addFile(fileContext);
            }
        } else
        if (f.isDirectory()) {
            Properties newContext = null;
            File x = new File(f,"_info.txt");
            if (x.exists() && x.isFile()) {
                newContext = readFile(x, job, context);
            } else {
                newContext = new Properties();
                newContext.putAll(context);
            }
            newContext.put("topicName", f.getName());
            job.addTopic(newContext);
            
            for (File n : f.listFiles()) {
                if (!n.getName().startsWith(".") && !n.getName().startsWith("_"))
                    readSources(n, job, context);
            }
        }
        
        
    }

    private Properties readFile(File f, Job job, Properties context) throws IOException {
        Properties out = new Properties();
        out.putAll(context);
        out.put("name", MFile.getFileNameOnly(f.getName()));
        
        List<String> lines = MFile.readLines(f, true);
        
        Iterator<String> iter = lines.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            if (line.trim().length() == 0) break;
            
            int pos = line.indexOf(':');
            String key = line.substring(0, pos).trim();
            String value = line.substring(pos+1);
            while (value.endsWith("\\")) {
                value = value.substring(0, value.length()-1) + iter.next();
            }
            value = value.trim();
            out.put(key, value);
        }

        StringBuilder text = new StringBuilder();
        while (iter.hasNext()) {
            String line = iter.next();
            if (text.length() != 0)
                text.append(" ");
            text.append(line);
        }
        out.put("text", text.toString());

        return out;
    }

    private void createSimpleConfiguration(LinkedList<Configuration> config) {
        Configuration c = new Configuration();
        c.enabled = true;
        c.ignoreFails = ignoreFails;
        c.sources = sources;
        c.template = template;
        c.output = output;
        c.filterGroups = filterGroups;
        c.parameters = new Properties();
        if (parameters != null)
            c.parameters.putAll(parameters);
        config.add(c);
    }

    private void readConfigurationFile(File f, LinkedList<Configuration> config) throws MojoExecutionException {
        
        try {
            Document doc = MXml.loadXml(f);
            for (Element eConfig : MXml.getLocalElementIterator(doc.getDocumentElement(), "configuration")) {
                Configuration c = new Configuration();
                c.enabled = MCast.toboolean(MXml.getValue(eConfig, "enabled", ""), true);
                c.ignoreFails = MCast.toboolean(MXml.getValue(eConfig, "ignoreFails", ""), ignoreFails);
                c.sources = MCast.toString(MXml.getValue(eConfig, "sources", ""), sources);
                c.template = MCast.toString(MXml.getValue(eConfig, "template", ""), template);
                c.output = MCast.toString(MXml.getValue(eConfig, "output", ""), output);
                c.filterGroups = MCast.toString(MXml.getValue(eConfig, "filterGroups", ""), filterGroups);
                // read parameters
                c.parameters = new Properties();
                if (parameters != null)
                    c.parameters.putAll(parameters);
                Element eParameters = MXml.getElementByPath(eConfig, "parameters");
                if (eParameters != null) {
                    for (Element eParam : MXml.getLocalElementIterator(eParameters)) {
                        c.parameters.put(eParam.getNodeName(), MXml.getValue(eParam, false));
                    }
                }

                config.add(c);
            }
        } catch (Exception e) {
            throw new MojoExecutionException(f.getAbsolutePath(), e);
        }
    }

}

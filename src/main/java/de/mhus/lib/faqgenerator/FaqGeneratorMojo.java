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
import org.jtwig.environment.EnvironmentConfiguration;
import org.jtwig.environment.EnvironmentConfigurationBuilder;
import org.jtwig.functions.FunctionRequest;
import org.jtwig.functions.JtwigFunction;
import org.jtwig.functions.SimpleJtwigFunction;
import org.markdownj.MarkdownProcessor;
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
            File f = new File(toDirectory(configurationFile));
            if (!f.exists())
                throw new MojoExecutionException("Configuration file not found: " + f.getAbsolutePath());
            readConfigurationFile(f, config);
        } else {
            // create simple configuration
            createSimpleConfiguration(config);
        }
        
        getLog().debug("Configuration: " + config);
        
        // execute
        
        for (Configuration c : config) {
            if (c.enabled) {
                getLog().info("Create: " + c.sources + " to " + c.output);
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
        getLog().debug("read sources");
        readSources(job);
        
        // filter groups
        getLog().debug("filter groups");
        filterGroups(job);
        getLog().debug("cleanup topics");
        cleanupTopic(job);
        
        // sort findings
        getLog().debug("sort files");
        sortFiles(job.files);
        getLog().debug("sort topics");
        sortTopics(job.topics);
        
        // write template
        getLog().debug("create content");
        createContent(job);
    }

    @SuppressWarnings("unchecked")
    private void cleanupTopic(Job job) {
        // cleanup removed files from filter
        for (Properties topic : job.topics) {
            LinkedList<Properties> files = (LinkedList<Properties>)topic.get("topicFiles");
            if (files != null)
	            files.removeIf(i -> {
	                return !job.files.contains(i); // remove if no more exists in files list
	            });
        }
        
        // cleanup empty topics
        job.topics.removeIf(i -> {
            LinkedList<Properties> files = (LinkedList<Properties>)i.get("topicFiles");
            return files != null && files.isEmpty();
        });
        
    }

    @SuppressWarnings("unchecked")
    private void sortTopics(LinkedList<Properties> topics) {
        topics.sort((a,b) -> {
            String aOrder = a.getProperty("order");
            if (aOrder == null) aOrder = a.getProperty("topicName");
            
            String bOrder = b.getProperty("order");
            if (bOrder == null) bOrder = b.getProperty("topicName");
            if (aOrder == null && bOrder == null) return 0;
            if (aOrder == null) return -1;
            if (bOrder == null) return 1;
            return aOrder.compareTo(bOrder);
          });
        
        topics.forEach(t -> sortFiles( (LinkedList<Properties>)t.get("topicFiles") ) );
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

    private void createContent(Job job) throws MojoExecutionException, IOException {
        
        String template = job.config.template;
        
        // load template
        if (template == null) {
            try {
                template = project.getBuild().getOutputDirectory() + "/template.twig";
                new File(project.getBuild().getOutputDirectory()).mkdirs();
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
        JtwigFunction markdownFunction = new SimpleJtwigFunction() {
            MarkdownProcessor md = new MarkdownProcessor();
            @Override
            public String name() {
                return "markdown";
            }

            @Override
            public Object execute(FunctionRequest functionRequest) {
                return md.markdown(String.valueOf(functionRequest.getArguments().get(0)));
            }
        };
        
        EnvironmentConfiguration jtwigConfig = EnvironmentConfigurationBuilder
                .configuration()
                .functions()
                    .add(markdownFunction)
                .and()
            .build();
        JtwigTemplate jtwigTemplate = JtwigTemplate.fileTemplate(templateFile, jtwigConfig);
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
                newContext.setProperty("topicText", newContext.getProperty("text",""));
                newContext.remove("text");
            } else {
                newContext = new Properties();
                newContext.putAll(context);
            }
            job.addTopic(newContext, f.getName() );
            
            for (File n : f.listFiles()) {
                if (!n.getName().startsWith(".") && !n.getName().startsWith("_"))
                    readSources(n, job, newContext);
            }
        }
        
        
    }

    private Properties readFile(File f, Job job, Properties context) throws IOException {
        Properties out = new Properties();
        out.putAll(context);
        out.put("name", MFile.getFileNameOnly(f.getName()));
        out.remove("topicFiles");
        out.remove("topicText");
        
        List<String> lines = MFile.readLines(f, true);
        
        Iterator<String> iter = lines.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            if (line.trim().length() == 0) break; // empty line - end of header
            if (line.startsWith("#")) continue; // comments
            
            int pos = line.indexOf(':');
            if (pos < 0) continue; // invalid entry
            String key = line.substring(0, pos).trim();
            String value = line.substring(pos+1);
            while (value.endsWith("\\")) {
                value = value.substring(0, value.length()-1) + iter.next();
            }
            value = value.trim();
            // special case for groups - cumulate !!!
            if (key.equals("groups")) { 
                String oldGroups = out.getProperty("groups", "");
                value = oldGroups + (oldGroups.length() > 0 ? "," : "") + value;
            }
            // end
            out.put(key, value);
        }

        StringBuilder text = new StringBuilder();
        while (iter.hasNext()) {
            String line = iter.next();
            if (text.length() != 0)
                text.append("\n");
            text.append(line);
        }
        out.put("text", text.toString());

        return out;
    }

    private void createSimpleConfiguration(LinkedList<Configuration> config) {
        Configuration c = new Configuration();
        c.enabled = true;
        c.ignoreFails = ignoreFails;
        c.sources = toDirectory( sources );
        c.template = toDirectory( template );
        c.output = toDirectory( output );
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
                c.sources = toDirectory( MCast.toString(MXml.getValue(eConfig, "sources", ""), sources) );
                c.template = toDirectory( MCast.toString(MXml.getValue(eConfig, "template", ""), template) );
                c.output = toDirectory( MCast.toString(MXml.getValue(eConfig, "output", ""), output) );
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

    private String toDirectory(String in) {
        if (MString.isEmpty(in)) return in;
        in = in.replace("${base}", project.getBasedir().getAbsolutePath() );
        in = in.replace("${build}", project.getBuild().getDirectory() );
        return in;
    }

}

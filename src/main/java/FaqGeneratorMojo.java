import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(
        name = "faq-generate", 
        defaultPhase = LifecyclePhase.PROCESS_SOURCES, 
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, 
        inheritByDefault = true
    )
public class FaqGeneratorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;
    
//  @Parameter(defaultValue = "${project.build.directory}/generated/mhus-const")
    @Parameter
    protected String outputDirectory;

    @Parameter
    protected String template = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // TODO Auto-generated method stub
        
    }

}

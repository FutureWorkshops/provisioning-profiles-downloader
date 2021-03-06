package com.futureworkshops.jenkins.ppd;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.*;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.CopyOnWriteMap;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ProvisioningProfilesWrapper extends BuildWrapper {

    private boolean deleteProfilesAfterBuild;
    private boolean overwriteExistingProfiles;
    private String developerTeamID;
    private String developerPortalCredentialsID;
    private List<ProvisioningProfileParam> provisioningProfiles = Collections.emptyList();

    private transient List<FilePath> copiedProfiles;
    private transient Map<String, ProvisioningProfileParam> provisioningParamToUDID;

    @DataBoundConstructor
    public ProvisioningProfilesWrapper(String developerTeamID,
                                       String developerPortalCredentialsID,
                                       List<ProvisioningProfileParam> provisioningProfiles,
                                       boolean deleteProfilesAfterBuild,
                                       boolean overwriteExistingProfiles) {
        super();

        this.developerTeamID = developerTeamID;
        this.developerPortalCredentialsID = developerPortalCredentialsID;
        this.provisioningProfiles = provisioningProfiles;

        this.deleteProfilesAfterBuild = deleteProfilesAfterBuild;
        this.overwriteExistingProfiles = overwriteExistingProfiles;

    }

    public String getDeveloperTeamID() {
        return developerTeamID;
    }

    public String getDeveloperPortalCredentialsID() { return developerPortalCredentialsID; }

    public List<ProvisioningProfileParam> getProvisioningProfiles() { return provisioningProfiles; }

    public boolean getDeleteProfilesAfterBuild() {
        return deleteProfilesAfterBuild;
    }

    public boolean getOverwriteExistingProfiles() {
        return overwriteExistingProfiles;
    }

    @Override
    public Environment setUp(AbstractBuild build,
                             Launcher launcher,
                             BuildListener listener) throws IOException, InterruptedException {

        FilePath projectWorkspace = build.getWorkspace();
        this.getScriptPath().copyTo(new FilePath(projectWorkspace, "src/main/webapp/script.rb"));
        listener.getLogger().println(this.getScriptPath().getRemote());
        UsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider
                        .lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
                                new LinkedList<DomainRequirement>()),
                CredentialsMatchers.withId(this.getDeveloperPortalCredentialsID()));

        String username = credentials.getUsername();
        String password = credentials.getPassword().getPlainText();
        String teamID = this.getDeveloperTeamID();
        FilePath localDest = new FilePath(projectWorkspace, "binary");

        if(credentials != null) {
            localDest.mkdirs();
            this.copiedProfiles = new LinkedList<FilePath>();
            this.provisioningParamToUDID = new HashMap<String, ProvisioningProfileParam>();
            for(ProvisioningProfileParam param : this.provisioningProfiles) {
                Launcher.ProcStarter procStarter = launcher.launch();
                procStarter = procStarter.pwd(projectWorkspace)
                        .cmds("ruby", "src/main/webapp/script.rb", username, password, teamID, param.getProvisioningProfileName(), localDest.getRemote())
                        .quiet(true)
                        .stdout(listener.getLogger())
                        .stderr(listener.getLogger());
                Proc proc = launcher.launch(procStarter);
                int retcode = proc.join();
                if (retcode != 0) {
                    build.setResult(Result.FAILURE);
                    return null;
                }
                FilePath[] UUIDs = localDest.list("*.uuid");
                if(UUIDs.length > 0) {
                    String UUID = UUIDs[0].getBaseName();
                    UUIDs[0].delete();

                    FilePath realPath = new FilePath(FilePath.getHomeDirectory(projectWorkspace.getChannel()), "Library/MobileDevice/Provisioning Profiles/" + UUID + ".mobileprovision");
                    this.copiedProfiles.add(realPath);
                    this.provisioningParamToUDID.put(UUID, param);
                }
            }
        }

        return new ProvisioningProfilesWrapper.EnvironmentImpl();
    }

    private FilePath getScriptPath() {
        String resourceDir = Jenkins.getInstance().getPlugin("provisioning-profiles-downloader").getWrapper().baseResourceURL.getFile();
        File scriptFile = new File(resourceDir, "script.rb");
        return new FilePath(scriptFile);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }


    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> ap) {
            return true;
        }

        @Override
        public String getDisplayName() { return "Provisioning Profiles Wrapper"; }

        public ListBoxModel doFillDeveloperPortalCredentialsIDItems(ItemGroup context) {

            return new StandardUsernameListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class,
                            context,
                            null,
                            new LinkedList<DomainRequirement>()));
        }
    }

    /**
     * Environment implementation that adds additional variables to the build.
     */
    private class EnvironmentImpl extends Environment {
       // private final List<KPPProvisioningProfile> provisioningProfiles;

        /**
         * Constructor
         *
         * @param provisioningProfiles list of provisioning profiles configured for this job
         */
        public EnvironmentImpl() {
            super();
         //   this.provisioningProfiles = provisioningProfiles;
        }

        /**
         * Adds additional variables to the build environment.
         *
         * @return environment with additional variables
         */
        private Map<String, String> getEnvMap() {
            Map<String, String> map = new HashMap<String, String>();
            for(Map.Entry<String, ProvisioningProfileParam> entry : provisioningParamToUDID.entrySet()) {
                String UUID = entry.getKey();
                String variableName = entry.getValue().getProvisioningProfileVariableName();
                map.put(variableName, UUID);
            }
            return map;
        }

        @Override
        public void buildEnvVars(Map<String, String> env) {
            env.putAll(getEnvMap());
        }

        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener)
                throws IOException, InterruptedException {
            if (deleteProfilesAfterBuild) {
                for (FilePath filePath : copiedProfiles) {
                    filePath.delete();
                }
            }
            return true;
        }

    }

}

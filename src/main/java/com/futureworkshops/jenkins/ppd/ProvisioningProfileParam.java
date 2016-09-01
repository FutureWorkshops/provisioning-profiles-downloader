package com.futureworkshops.jenkins.ppd;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by dune on 25/08/2016.
 */
public class ProvisioningProfileParam extends AbstractDescribableImpl<ProvisioningProfileParam> {
    private String provisioningProfileName;
    private String provisioningProfileVariableName;

    @DataBoundConstructor
    public ProvisioningProfileParam(String provisioningProfileName, String provisioningProfileVariableName) {
        this.provisioningProfileName = provisioningProfileName;
        this.provisioningProfileVariableName = provisioningProfileVariableName;
    }

    public String getProvisioningProfileName() {
        return this.provisioningProfileName;
    }
    public String getProvisioningProfileVariableName() {
        return this.provisioningProfileVariableName;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ProvisioningProfileParam> {
        public String getDisplayName() { return "Provisioning Profile parameters"; }
    }
}

def call() {
    script {
        //Failed stage
        env.FAILED_STAGE=""
        //VMs setup
        if ( env.POWERVS == "true" ) {
            env.NETWORK_NAME = "ocp-net"
            env.RHEL_USERNAME = "root"
            env.RHEL_SMT = "4"
            env.CLUSTER_DOMAIN = "redhat.com"
            env.ENABLE_LOCAL_REGISTRY = "false"
            env.LOCAL_REGISTRY_IMAGE = "docker.io/ibmcom/registry-ppc64le:2.6.2.5"
            //Needed for target service
            env.CRN = "crn:v1:bluemix:public:power-iaas:tor01:a/7cfbd5381a434af7a09289e795840d4e:007e0e92-91d5-4f30-bc63-ca515660a4c2::"

            // Bellow 4 variables are not used. Disabled in template
            env.HELPERNODE_REPO = "https://github.com/RedHatOfficial/ocp4-helpernode"
            env.HELPERNODE_TAG = ""
            env.INSTALL_PLAYBOOK_REPO = "https://github.com/ocp-power-automation/ocp4-playbooks"
            env.INSTALL_PLAYBOOK_TAG = ""
            env.CNI_NETWORK_PROVIDER = "OVNKubernetes"
            //Upgrade variables
            env.UPGRADE_IMAGE = ""
            env.UPGRADE_PAUSE_TIME = "90"
            env.UPGRADE_DELAY_TIME = "600"
            env.FIPS_COMPLIANT = "false"
            if ( env.ODF_VERSION!= null && !env.ODF_VERSION.isEmpty() ) {
                env.INSTANCE_NAME = "rdr-cicd-odf"
                env.SETUP_SQUID_PROXY = "false"
                env.STORAGE_TYPE = "notnfs"
                env.SYSTEM_TYPE = "e980"
                env.RERUN_TIER_TEST = "2"
                env.PRE_KERNEL_OPTIONS='\\"rd.multipath=0\\", \\"loglevel=7\\"'
            }
            else {
                env.INSTANCE_NAME = "rdr-cicd"
                env.SETUP_SQUID_PROXY = "true"
                env.STORAGE_TYPE = "nfs"
                env.SYSTEM_TYPE = "s922"
                //E2e Variables
                env.E2E_GIT = "https://github.com/openshift/origin"
                env.E2E_BRANCH="release-${env.OCP_RELEASE}"
                if ( env.OCP_RELEASE == "4.8" || env.OCP_RELEASE == "4.9" || env.OCP_RELEASE == "4.10" ||  env.OCP_RELEASE == "4.11" ) {
                    env.CNI_NETWORK_PROVIDER = "OpenshiftSDN" 
                    env.E2E_EXCLUDE_LIST = "https://raw.github.ibm.com/redstack-power/e2e-exclude-list/${env.OCP_RELEASE}-powervs/ocp${env.OCP_RELEASE}_power_exclude_list.txt"
                }
                else {
                    env.E2E_EXCLUDE_LIST = "https://raw.github.ibm.com/redstack-power/e2e-exclude-list/${env.OCP_RELEASE}-powervs/ocp${env.OCP_RELEASE}_power_exclude_list_OVNKubernetes.txt"
                }
                //Scale variables
                env.SCALE_NUM_OF_DEPLOYMENTS = "60"
                env.SCALE_NUM_OF_NAMESPACES = "1000"
            }
            //Slack message
            env.MESSAGE=""

            env.DEPLOYMENT_STATUS = false
            env.BASTION_IP = ""

            //Pull Secret
            env.PULL_SECRET_FILE = "${WORKSPACE}/deploy/data/pull-secret.txt"
            //Need to use latest build when 4.14 releases
            if (env.OCP_RELEASE == "4.16") {
                env.OPENSHIFT_INSTALL_TARBALL="https://mirror.openshift.com/pub/openshift-v4/ppc64le/clients/ocp-dev-preview/candidate-4.16/openshift-install-linux.tar.gz"
                env.OPENSHIFT_CLIENT_TARBALL="https://mirror.openshift.com/pub/openshift-v4/ppc64le/clients/ocp-dev-preview/candidate-4.16/openshift-client-linux.tar.gz"
                env.OPENSHIFT_CLIENT_TARBALL_AMD64="https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp-dev-preview/candidate-4.16/openshift-client-linux.tar.gz"
            }
            else {
                env.OPENSHIFT_INSTALL_TARBALL="https://mirror.openshift.com/pub/openshift-v4/ppc64le/clients/ocp/latest-${OCP_RELEASE}/openshift-install-linux.tar.gz"
                env.OPENSHIFT_CLIENT_TARBALL="https://mirror.openshift.com/pub/openshift-v4/ppc64le/clients/ocp/latest-${OCP_RELEASE}/openshift-client-linux.tar.gz"
                env.OPENSHIFT_CLIENT_TARBALL_AMD64="https://mirror.openshift.com/pub/openshift-v4/clients/ocp/latest-${OCP_RELEASE}/openshift-client-linux.tar.gz"
            }
        }
        else {
            //PowerVC ENV Variables
            env.OS="linux"
            env.OS_IDENTITY_API_VERSION="3"
            env.OS_REGION_NAME="RegionOne"
            env.OS_PROJECT_DOMAIN_NAME="Default"
            env.OS_PROJECT_NAME="ibm-default"
            env.OS_TENANT_NAME="ibm-default"
            env.OS_USER_DOMAIN_NAME="Default"
            env.OS_COMPUTE_API_VERSION="2.46"
            env.OS_NETWORK_API_VERSION="2.0"
            env.OS_IMAGE_API_VERSION="2"
            env.OS_VOLUME_API_VERSION="3"
            env.OS_NETWORK="icp_network4"
            env.OS_PRIVATE_NETWORK="icp_network4"
            env.MASTER_TEMPLATE="${JOB_BASE_NAME}"+"-"+"${BUILD_NUMBER}"+"-"+"mas"
            env.WORKER_TEMPLATE="${JOB_BASE_NAME}"+"-"+"${BUILD_NUMBER}"+"-"+"wor"
            env.BOOTSTRAP_TEMPLATE="${JOB_BASE_NAME}"+"-"+"${BUILD_NUMBER}"+"-"+"boo"
            env.BASTION_TEMPLATE="${JOB_BASE_NAME}"+"-"+"${BUILD_NUMBER}"+"-"+"bas"
            env.RHEL_USERNAME = "root"
            env.OS_INSECURE = true

            //Upgrade variables
            env.UPGRADE_IMAGE = ""
            env.UPGRADE_PAUSE_TIME = "90"
            env.UPGRADE_DELAY_TIME = "600"

            // Pull secrets
            env.PULL_SECRET_FILE = "${WORKSPACE}/deploy/data/pull-secret.txt"

            env.OPENSHIFT_POWERVC_GIT_TF_DEPLOY_PROJECT="https://github.com/sudeeshjohn/ocp4-upi-powervm.git"
            //Cluster and vm details
            env.CLUSTER_DOMAIN="redhat.com"
            env.INSTANCE_NAME = "rdr-cicd"
            env.MOUNT_ETCD_RAMDISK="true"
            env.CHRONY_CONFIG="true"
            env.SCG_ID = "213343ac-cd7f-47b7-a466-7a8c65ed8985"
            env.VOLUME_STORAGE_TEMPLATE = "c340f1_v7k base template"
            env.CNI_NETWORK_PROVIDER = "OVNKubernetes"
            env.CONNECTION_TIME_OUT = "30"
            env.STORAGE_TYPE = "nfs"
            env.FIPS_COMPLIANT = "false"
            if ( env.ODF_VERSION!= null && !env.ODF_VERSION.isEmpty() ) {
                env.INSTANCE_NAME = "rdr-cicd-odf"
                env.SETUP_SQUID_PROXY = "false"
                env.STORAGE_TYPE = "notnfs"
            }
            //e2e variables
            if ( env.ENABLE_E2E_TEST ) {
                env.E2E_GIT="https://github.com/openshift/origin"
                env.E2E_BRANCH="release-${env.OCP_RELEASE}"
                env.ENABLE_E2E_UPGRADE="false"
            }
            if ( env.OCP_RELEASE == "4.8" || env.OCP_RELEASE == "4.9" || env.OCP_RELEASE == "4.10" ||  env.OCP_RELEASE == "4.11" ) {
                env.CNI_NETWORK_PROVIDER = "OpenshiftSDN"
                env.E2E_EXCLUDE_LIST="https://raw.github.ibm.com/redstack-power/e2e-exclude-list/${env.OCP_RELEASE}-powervm/ocp${env.OCP_RELEASE}_power_exclude_list.txt"     
            } else {
                env.E2E_EXCLUDE_LIST = "https://raw.github.ibm.com/redstack-power/e2e-exclude-list/${env.OCP_RELEASE}-powervm/ocp${env.OCP_RELEASE}_power_exclude_list_OVNKubernetes.txt"
            }

            //Scale test variables
            if ( env.ENABLE_SCALE_TEST ) {
                env.SCALE_NUM_OF_DEPLOYMENTS = "60"
                env.SCALE_NUM_OF_NAMESPACES = "1000"
                env.EXPOSE_IMAGE_REGISTRY = "false"
            }

            //Proxy setup
            env.SETUP_SQUID_PROXY = "false"
            env.PROXY_ADDRESS = ""

            //Slack message
            env.MESSAGE=""

            env.DEPLOYMENT_STATUS = false
            env.BASTION_IP = ""
            //Common Service
            env.CS_INSTALL = "false"

            env.HELPERNODE_REPO = "https://github.com/RedHatOfficial/ocp4-helpernode"
            env.HELPERNODE_TAG = ""
            env.INSTALL_PLAYBOOK_REPO = "https://github.com/ocp-power-automation/ocp4-playbooks"
            env.INSTALL_PLAYBOOK_TAG = ""

            // Compute Template Variables
            env.WORKER_MEMORY_MB=""
            env.MASTER_MEMORY_MB=""
            env.BASTION_MEMORY_MB=""
            env.BOOTSTRAP_MEMORY_MB=''
            //Need to use latest build when 4.15 releases
            if (env.OCP_RELEASE == "4.16") {
                env.OPENSHIFT_INSTALL_TARBALL="https://mirror.openshift.com/pub/openshift-v4/ppc64le/clients/ocp-dev-preview/candidate-4.16/openshift-install-linux.tar.gz"
                env.OPENSHIFT_CLIENT_TARBALL="https://mirror.openshift.com/pub/openshift-v4/ppc64le/clients/ocp-dev-preview/candidate-4.16/openshift-client-linux.tar.gz"
                env.OPENSHIFT_CLIENT_TARBALL_AMD64="https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp-dev-preview/candidate-4.16/openshift-client-linux.tar.gz"
            }
            else {
                env.OPENSHIFT_INSTALL_TARBALL="https://mirror.openshift.com/pub/openshift-v4/ppc64le/clients/ocp/latest-${OCP_RELEASE}/openshift-install-linux.tar.gz"
                env.OPENSHIFT_CLIENT_TARBALL="https://mirror.openshift.com/pub/openshift-v4/ppc64le/clients/ocp/latest-${OCP_RELEASE}/openshift-client-linux.tar.gz"
                env.OPENSHIFT_CLIENT_TARBALL_AMD64="https://mirror.openshift.com/pub/openshift-v4/clients/ocp/latest-${OCP_RELEASE}/openshift-client-linux.tar.gz"
            }
            if ( env.ODF_VERSION!= null && !env.ODF_VERSION.isEmpty() ) {
                env.INSTANCE_NAME = "rdr-cicd-odf"
                env.SETUP_SQUID_PROXY = "false"
                env.STORAGE_TYPE = "notnfs"
                env.RERUN_TIER_TEST = "2"
            }
        }
    }
}

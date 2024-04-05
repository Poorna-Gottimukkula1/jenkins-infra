#!/bin/bash

ansible-galaxy collection install community.general #[temp fix]The Ansible playbook fails when a role requires the `make` module or when running all playbooks using `playbooks/main.yaml`
echo "Setting up htpasswd"
git clone https://github.com/ocp-power-automation/ocp4-playbooks-extras
cd ocp4-playbooks-extras
cp examples/inventory inventory
cp examples/all.yaml .
sed -i 's/htpasswd_identity_provider: false/htpasswd_identity_provider: true/g' all.yaml
sed -i 's/htpasswd_username: ""/htpasswd_username: "testuser"/g' all.yaml
sed -i 's/htpasswd_password: ""/htpasswd_password: "testuser"/g' all.yaml
sed -i 's/htpasswd_user_role: ""/htpasswd_user_role: "self-provisioner"/g' all.yaml
ansible-playbook  -i inventory -e @all.yaml playbooks/main.yml

echo "Setting up environmental variables"
OC_URL=$(oc whoami --show-server)
OC_URL=$(echo $OC_URL | cut -d':' -f2 | tr -d [/])
OC_CONSOLE_URL=$(oc whoami --show-console)
export BUSHSLICER_DEFAULT_ENVIRONMENT=ocp4
export OPENSHIFT_ENV_OCP4_HOSTS=$OC_URL:lb
export OPENSHIFT_ENV_OCP4_USER_MANAGER_USERS=testuser:testuser
export OPENSHIFT_ENV_OCP4_ADMIN_CREDS_SPEC=file:///root/openstack-upi/auth/kubeconfig
export BUSHSLICER_CONFIG="{'global': {'browser': 'firefox'}, 'environments': {'ocp4': {'admin_creds_spec': '/root/openstack-upi/auth/kubeconfig', 'api_port': '6443', 'web_console_url': '${OC_CONSOLE_URL}', 'version': '${OCP_RELEASE}.0'}}}"
# export BUSHSLICER_CONFIG='
# global:
#   browser: firefox
# environments:
#   ocp4:
#     admin_creds_spec: /root/openstack-upi/auth/kubeconfig
#     version: "4.12.0"
#     #api_port: 443      # For HA clusters, both 3.x and 4.x
#     #api_port: 6443     # For non-HA 4.x clusters
#     #api_port: 8443     # For non-HA 3.x clusters
#     #web_console_url: https://console-openshift-console.apps.*.openshift.com
# '
echo $BUSHSLICER_DEFAULT_ENVIRONMENT
echo $OPENSHIFT_ENV_OCP4_HOSTS
echo $OPENSHIFT_ENV_OCP4_USER_MANAGER_USERS
echo $OPENSHIFT_ENV_OCP4_ADMIN_CREDS_SPEC
echo $BUSHSLICER_CONFIG

cd ../
echo "Setting up environment for verification tests"
sudo yum module list ruby
sudo dnf module reset ruby -y
sudo yum install -y @ruby:3.0
ruby --version

echo "Cloning verification-tests repo"
git clone https://github.com/openshift/verification-tests
cd verification-tests
sed -i "s/gem 'azure-storage'/#gem 'azure-storage'/g" Gemfile
sed -i "s/gem 'azure_mgmt_storage'/#gem 'azure_mgmt_storage'/g" Gemfile
sed -i "s/gem 'azure_mgmt_compute'/#gem 'azure_mgmt_compute'/g" Gemfile
sed -i "s/gem 'azure_mgmt_resources'/#gem 'azure_mgmt_resources'/g" Gemfile
sed -i "s/gem 'azure_mgmt_network'/#gem 'azure_mgmt_network'/g" Gemfile
sed -i "s/BUSHSLICER_DEBUG_AFTER_FAIL=true/BUSHSLICER_DEBUG_AFTER_FAIL=false/g" config/cucumber.yml
sudo ./tools/install_os_deps.sh
./tools/hack_bundle.rb
bundle update
bundle exec cucumber --tags @ppc64le

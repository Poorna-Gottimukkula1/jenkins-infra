#!/bin/bash -x

echo "Setting up htpasswd"
htpasswd -c -B -b users.htpasswd testuser1 testuser1
oc create secret generic htpass-secret --from-file=htpasswd=users.htpasswd -n openshift-config

cat << EOF| oc apply -f -		
apiVersion: config.openshift.io/v1
kind: OAuth
metadata:
  name: cluster
spec:
  identityProviders:
  - name: my_htpasswd_provider
    mappingMethod: claim
    type: HTPasswd
    htpasswd:
      fileData:
        name: htpass-secret		
EOF

oc get secret htpass-secret -ojsonpath={.data.htpasswd} -n openshift-config | base64 --decode > users.htpasswd
htpasswd -bB users.htpasswd testuser2 testuser2
htpasswd -bB users.htpasswd testuser2 testuser3
htpasswd -bB users.htpasswd testuser2 testuser4
oc create secret generic htpass-secret --from-file=htpasswd=users.htpasswd --dry-run=client -o yaml -n openshift-config | oc replace -f -
echo "sleeping for 1m" && sleep 3m

oc login -u testuser1 -p testuser1
oc login -u testuser2 -p testuser2
oc login -u testuser3 -p testuser3
oc login -u testuser4 -p testuser4

echo "Setting up environmental variables"
OC_URL=$(oc whoami --show-server)
OC_URL=$(echo $OC_URL | cut -d':' -f2 | tr -d [/])
OC_CONSOLE_URL=$(oc whoami --show-console)
ver_cli=$(oc version --client | grep -i client | cut -d ' ' -f 3 | cut -d '.' -f1,2)
export BUSHSLICER_DEFAULT_ENVIRONMENT=ocp4
export OPENSHIFT_ENV_OCP4_HOSTS=$OC_URL:lb
export OPENSHIFT_ENV_OCP4_USER_MANAGER_USERS=testuser1:testuser1,testuser2:testuser2,testuser3:testuser3,testuser4:testuser4
export OPENSHIFT_ENV_OCP4_ADMIN_CREDS_SPEC=file:///root/.kube/config
export BUSHSLICER_CONFIG="{'global': {'browser': 'firefox'}, 'environments': {'ocp4': {'admin_creds_spec': '/root/.kube/config', 'api_port': '6443', 'web_console_url': '${OC_CONSOLE_URL}', 'version': '${ver_cli}.0'}}}"
# export BUSHSLICER_CONFIG='
# global:
#   browser: firefox
# environments:
#   ocp4:
#     admin_creds_spec: /root/.kube/config
#     version: "4.15.0"
#     #api_port: 443      # For HA clusters, both 3.x and 4.x
#     api_port: 6443     # For non-HA 4.x clusters
#     #api_port: 8443     # For non-HA 3.x clusters
#     web_console_url: https://console-openshift-console.apps.*.openshift.com
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
sudo yum install -y @ruby:3.1
ruby --version

echo "Cloning verification-tests repo"
git clone git@github.com:openshift/verification-tests.git
cd verification-tests
sed -i "s/gem 'azure-storage'/#gem 'azure-storage'/g" Gemfile
sed -i "s/gem 'azure_mgmt_storage'/#gem 'azure_mgmt_storage'/g" Gemfile
sed -i "s/gem 'azure_mgmt_compute'/#gem 'azure_mgmt_compute'/g" Gemfile
sed -i "s/gem 'azure_mgmt_resources'/#gem 'azure_mgmt_resources'/g" Gemfile
sed -i "s/gem 'azure_mgmt_network'/#gem 'azure_mgmt_network'/g" Gemfile
sed -i "s/BUSHSLICER_DEBUG_AFTER_FAIL=true/BUSHSLICER_DEBUG_AFTER_FAIL=false/g" config/cucumber.yml
git clone git@github.com:openshift/cucushift.git features/tierN/
#rm -rf features/tierN/web
sudo ./tools/install_os_deps.sh
./tools/hack_bundle.rb
bundle update
bundle exec cucumber --no-color --tags '@ppc64le and @4.15 and @network-ovnkubernetes and not @inactive and not @destructive and not @fips and not @upgrade and @heterogeneous'
# cd features/tierN/ && git restore web && cd ../..
# bundle exec cucumber features/tierN/web/ --no-color --tags '@ppc64le and @4.15 and @network-ovnkubernetes and not @inactive and not @destructive and not @fips and not @upgrade and @heterogeneous'
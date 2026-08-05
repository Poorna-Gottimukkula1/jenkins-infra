#!/bin/bash

echo 'Creating ingress_vars.yaml'

# Clean ansible cache
rm -rf ~/.ansible

# Gather system facts
ansible all -m setup -a 'gather_subset=!all'

# Go to workspace
cd ${WORKSPACE}/ocp4-playbooks-extras

# Copy example file (assumes you have a template file)
cp examples/ocp_ingress_vars.yaml ocp_ingress_vars.yaml

# Update values
sed -i "s|ingress_firewall_enabled:.*$|ingress_firewall_enabled: true|g" ocp_ingress_vars.yaml
sed -i "s|ingress_catalogsource_image:.*$|ingress_catalogsource_image: ${INFO_CATALOGSOURCE_IMAGE}|g" ocp_ingress_vars.yaml
sed -i "s|ingress_catalogsource_name:.*$|ingress_catalogsource_name: qe-app-registry|g" ocp_ingress_vars.yaml
sed -i "s|ingress_namespace:.*$|ingress_namespace: openshift-ingress-node-firewall|g" ocp_ingress_vars.yaml
sed -i "s|ingress_directory:.*$|ingress_directory: /tmp/ingress|g" ocp_ingress_vars.yaml
sed -i "s|ingress_go_tarball:.*$|ingress_go_tarball: https://go.dev/dl/go1.22.4.linux-ppc64le.tar.gz|g" ocp_ingress_vars.yaml
sed -i "s|ingress_e2e:.*$|ingress_e2e: ${ENABLE_INFO_E2E_TEST}|g" ocp_ingress_vars.yaml
sed -i "s|ingress_e2e_github_repo:.*$|ingress_e2e_github_repo: https://github.com/openshift/openshift-tests-private|g" ocp_ingress_vars.yaml
sed -i "s|ingress_e2e_github_branch:.*$|ingress_e2e_github_branch: master|g" ocp_ingress_vars.yaml

# Print file
echo "==== ingress vars ===="
cat ocp_ingress_vars.yaml

# Inventory setup
cp examples/inventory ./ingress_inventory
sed -i "s|localhost|${BASTION_IP}|g" ingress_inventory
sed -i 's/ansible_connection=local/ansible_connection=ssh/g' ingress_inventory
sed -i "s|ssh|ssh ansible_ssh_private_key_file=${WORKSPACE}/deploy/id_rsa|g" ingress_inventory

echo "==== inventory ===="
cat ingress_inventory

# Show ansible config
echo "==== ansible.cfg ===="
cat ansible.cfg

# Run playbook
ansible-playbook -i ingress_inventory -e @ocp_ingress_vars.yaml playbooks/ocp-ingress-firewall-operator.yml

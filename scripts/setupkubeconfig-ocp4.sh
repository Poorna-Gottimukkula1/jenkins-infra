#!/bin/bash -x
echo "Capturing the System Information"
kube_config_path=${KUBECONFIG_PATH:="/root/openstack-upi/auth/kubeconfig"}
if [ -d ${WORKSPACE}/deploy ];then
    cd ${WORKSPACE}/deploy
else
    exit 1 
fi
# Capturing Terraform template
if [ ! -z "${BASTION_IP}" ]; then
    ssh -q -i id_rsa -o StrictHostKeyChecking=no root@${BASTION_IP} exit
    rc=$?
    if [ $? -eq 0 ] ; then
        rm -rf ~/.kube
        mkdir ~/.kube
        scp -i id_rsa -o StrictHostKeyChecking=no  root@${BASTION_IP}:${kube_config_path} ~/.kube/config
        fi 
        if [ ${POWERVS} == "false" ] ; then
            make terraform:output TERRAFORM_DIR=.${TARGET} TERRAFORM_OUTPUT_VAR=etc_hosts_entries >> /etc/hosts
        else
            make $TARGET:output TERRAFORM_OUTPUT_VAR=etc_hosts_entries >> /etc/hosts
        fi
        cat /etc/hosts
    else
        echo 'Unable to access the cluster. You may delete the VMs manually'
    fi
else
    echo 'Unable to access the cluster. You may delete the VMs manually'
fi
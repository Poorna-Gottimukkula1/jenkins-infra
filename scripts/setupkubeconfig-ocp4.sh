#!/bin/bash -x
    echo "Capturing the System Information"
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
            if [ ${OPENSHIFT_POWERVC_AI_SUBDIR} ]; then
               scp -i id_rsa -o StrictHostKeyChecking=no  root@${BASTION_IP}:/root/ocp4-workdir-assisted/auth/kubeconfig ~/.kube/config
               echo "Using the AI sub directory"
            else
               scp -i id_rsa -o StrictHostKeyChecking=no  root@${BASTION_IP}:/root/openstack-upi/auth/kubeconfig ~/.kube/config
               echo "Default directory"
            fi
            #scp -i id_rsa -o StrictHostKeyChecking=no  root@${BASTION_IP}:/root/ocp4-workdir-assisted/auth/kubeconfig ~/.kube/config  
            if [ ${POWERVS} == "false" ] ; then
                make terraform:output TERRAFORM_DIR=.${TARGET} TERRAFORM_OUTPUT_VAR=etc_hosts_entries >> /etc/hosts
            else
                make $TARGET:output TERRAFORM_OUTPUT_VAR=etc_hosts_entries >> /etc/hosts
            fi
        else
            echo 'Unable to access the cluster. You may delete the VMs manually'
        fi
    else
        echo 'Unable to access the cluster. You may delete the VMs manually'
    fi
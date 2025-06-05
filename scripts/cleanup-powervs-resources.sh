#!/bin/bash
while getopts "u:s:" opt
do
   case "$opt" in
      u ) cluster_id="$OPTARG" ;;
      s ) service_name="$OPTARG" ;;
      ? ) echo "Wrong Args" ;;
   esac
done
ibmcloud -v
if [ $? -ne 0 ]; then
   apt update; apt-get install -y wget
   wget https://download.clis.cloud.ibm.com/ibm-cloud-cli/2.34.1/IBM_Cloud_CLI_2.34.1_amd64.tar.gz --no-check-certificate
   tar -xvzf "./IBM_Cloud_CLI_2.34.1_amd64.tar.gz"
   ./Bluemix_CLI/install
   ibmcloud update -f
   ibmcloud config --check-version false
   ibmcloud plugin install power-iaas -f
   curl -sL https://raw.githubusercontent.com/ppc64le-cloud/pvsadm/master/get.sh | VERSION="v0.1.20" FORCE=1 bash
   
   if [ "rdr-qe-ocp-upi" = "$INSTANCE_NAME" ]; then 
      ibmcloud login -a cloud.ibm.com -r ${VPCREGION} -g ${RESOURCE_GROUP} -q --apikey=${IBMCLOUD_APIKEY}
   else
      ibmcloud login -a cloud.ibm.com -r us-south -g ibm-internal-cicd-resource-group -q --apikey=${IBMCLOUD_APIKEY}
   fi
   ibmcloud pi workspace target "${CRN}"
fi

if [ -n "$service_name" ]; then
  #Cleaning from clean job
  #Purge ssh keys
  pvsadm purge keys --workspace-name $service_name  --regexp "$cluster_id*" --no-prompt --ignore-errors

  #Purge vms
  pvsadm purge vms --workspace-name $service_name  --regexp "$cluster_id*" --no-prompt --ignore-errors

  #Purge volumes
  pvsadm purge volumes --workspace-name $service_name  --regexp "$cluster_id*" --no-prompt --ignore-errors

  #Added sleep to give time to delete vms
  sleep 300
  #Purge networks
  pvsadm purge networks --workspace-name $service_name  --regexp "$cluster_id*" --no-prompt --ignore-errors
else
  #Cleaning as a part of script
  #Purge vms
  pvsadm purge vms --workspace-id ${SERVICE_INSTANCE_ID}  --regexp "$cluster_id*" --no-prompt --ignore-errors

  #Purge volumes
  pvsadm purge volumes --workspace-id ${SERVICE_INSTANCE_ID}  --regexp "$cluster_id*" --no-prompt --ignore-errors

  #Added sleep to give time to delete vms
  sleep 300
  #Purge networks
  pvsadm purge networks --workspace-id ${SERVICE_INSTANCE_ID}  --regexp "$cluster_id*" --no-prompt --ignore-errors
fi

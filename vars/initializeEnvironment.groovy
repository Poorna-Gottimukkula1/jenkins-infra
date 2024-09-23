def call(String region="us-south",resource_group="ibm-internal-cicd-resource-group") {
    script {
      ansiColor('xterm') {
           echo ""
      }
      try {
          env.REGION=region
          env.RESOURCE_GROUP=resource_group
          if ( env.SERVICE_INSTANCE_GUID != ""  ){
              env.SERVICE_INSTANCE_ID=env.SERVICE_INSTANCE_GUID
          }
           sh '''
            echo 'Initializing supporting repos and keys !'
            cd ${WORKSPACE}/deploy
            make init
            make keys
            make setup-dependencies
            if [ "${POWERVS}" = "true" ] ; then
                ibmcloud update -f
                ibmcloud plugin update --all
                curl -sL https://raw.githubusercontent.com/ppc64le-cloud/pvsadm/v0.1.15/get.sh | VERSION="v0.1.15" FORCE=1 bash
                ibmcloud login -a cloud.ibm.com -r ${REGION} -g ${RESOURCE_GROUP} -q --apikey=${IBMCLOUD_API_KEY}
                ibmcloud target -r ${REGION} -g ${RESOURCE_GROUP}
                CRN=$(ibmcloud pi workspace ls  | grep "${SERVICE_INSTANCE_ID}" |awk '{print $1}')
                ibmcloud pi workspace target "$CRN"
            fi
            # Setting oc client
	    if [ ${OPENSHIFT_CLIENT_TARBALL_AMD64} ]; then
	        wget --quiet --no-check-certificate "${OPENSHIFT_CLIENT_TARBALL_AMD64}" -O - | tar -xz
	        cp kubectl oc /usr/bin/
	    fi
           '''
      }
      catch (err) {
           echo 'Error ! ENV setup failed!'
           env.FAILED_STAGE=env.STAGE_NAME
           throw err
      }
   }
}

FROM jenkins/inbound-agent:3206.vb_15dcf73f6a_9-10
USER root
RUN apt-get update && apt install curl gnupg2 --yes \
        python3 \
        python3-pip \
        make \
        unzip \
        jq \
        pkg-config \
        wget \
        git \
        curl \
        sudo \
        rsync \
        dnsutils \
        iproute2 \
    && rm -rf /var/lib/apt/lists/* && rm /usr/lib/python3.*/EXTERNALLY-MANAGED
RUN wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt update \
    && apt install ./google-chrome-stable_current_amd64.deb -y \
    && rm -rf ./google-chrome-stable_current_amd64.deb
RUN wget https://download.clis.cloud.ibm.com/ibm-cloud-cli/2.24.0/IBM_Cloud_CLI_2.24.0_amd64.tar.gz \
        && tar -xvzf "./IBM_Cloud_CLI_2.24.0_amd64.tar.gz" \
        &&  ./Bluemix_CLI/install \
        && rm -rf "./IBM_Cloud_CLI_2.24.0_amd64.tar.gz" \
        && rm -rf "./Bluemix_CLI/" \
        && ibmcloud update -f \
        && ibmcloud plugin install cloud-object-storage -f \
        && ibmcloud plugin install container-service -f \
        && ibmcloud plugin install power-iaas -f
RUN pip install --upgrade pip ; pip install python-openstackclient; pip install ansible; pip install yq
ENV PATH $GOPATH/bin:/usr/local/go/bin:$PATH
RUN ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa
ENV VERSION="1.7.6"
RUN wget https://github.com/containerd/nerdctl/releases/download/v$VERSION/nerdctl-$VERSION-linux-amd64.tar.gz
RUN sudo tar zxvf  nerdctl-$VERSION-linux-amd64.tar.gz -C  /usr/local/bin/
RUN rm -f nerdctl-$VERSION-linux-amd64.tar.gz
RUN sed -i '2 a ip link set dev eth0 mtu 1420' /usr/local/bin/jenkins-agent
#===============================
FROM jenkins/inbound-agent:3206.vb_15dcf73f6a_9-10
USER root
RUN apt-get update && apt install curl gnupg2 --yes \
        python3 \
        python3-pip \
        make \
        unzip \
        jq \
        pkg-config \
        wget \
        git \
        curl \
        sudo \
        rsync \
        dnsutils \
        iproute2 \
        openstack-clients \
        ansible \
        yq \
    && rm -rf /var/lib/apt/lists/*
RUN wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt update \
    && apt install ./google-chrome-stable_current_amd64.deb -y \
    && rm -rf ./google-chrome-stable_current_amd64.deb
RUN wget https://download.clis.cloud.ibm.com/ibm-cloud-cli/2.24.0/IBM_Cloud_CLI_2.24.0_amd64.tar.gz \
        && tar -xvzf "./IBM_Cloud_CLI_2.24.0_amd64.tar.gz" \
        &&  ./Bluemix_CLI/install \
        && rm -rf "./IBM_Cloud_CLI_2.24.0_amd64.tar.gz" \
        && rm -rf "./Bluemix_CLI/" \
        && ibmcloud update -f \
        && ibmcloud plugin install cloud-object-storage -f \
        && ibmcloud plugin install container-service -f \
        && ibmcloud plugin install power-iaas -f
#RUN pip3 install --upgrade pip ; pip3 install python-openstackclient; pip3 install ansible; pip3 install yq
ENV PATH $GOPATH/bin:/usr/local/go/bin:$PATH
RUN ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa
ENV VERSION="1.7.6"
RUN wget https://github.com/containerd/nerdctl/releases/download/v$VERSION/nerdctl-$VERSION-linux-amd64.tar.gz
RUN sudo tar zxvf  nerdctl-$VERSION-linux-amd64.tar.gz -C  /usr/local/bin/
RUN rm -f nerdctl-$VERSION-linux-amd64.tar.gz
RUN sed -i '2 a ip link set dev eth0 mtu 1420' /usr/local/bin/jenkins-agent
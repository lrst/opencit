#!/bin/bash
# WARNING:
# *** do NOT use TABS for indentation, use SPACES
# *** TABS will cause errors in some linux distributions

# SCRIPT CONFIGURATION:
script_name=pcactl
intel_conf_dir=/etc/intel/cloudsecurity
package_name=privacyca
package_dir=/opt/intel/cloudsecurity/${package_name}
package_config_filename=${intel_conf_dir}/${package_name}.properties
package_env_filename=${package_dir}/${package_name}.env
package_install_filename=${package_dir}/${package_name}.install
#package_name_rpm=AttestationService
#package_name_deb=attestationservice
#package_setup_cmd="asctl setup"
#glassfish_required_version=4.0
glassfish_parent_dir=/usr/share
#tomcat_required_version=7.0.34
#tomcat_parent_dir=/usr/lib
#tomcat_name=apache-tomcat-7.0.34
#java_required_version=1.7.0_51
#APPLICATION_YUM_PACKAGES="make gcc openssl libssl-dev"
#APPLICATION_APT_PACKAGES="dpkg-dev make gcc openssl libssl-dev"
webservice_application_name=HisPrivacyCAWebServices2

# FUNCTION LIBRARY, VERSION INFORMATION, and LOCAL CONFIGURATION
if [ -f "${package_dir}/functions" ]; then . "${package_dir}/functions"; else echo "Missing file: ${package_dir}/functions"; exit 1; fi
if [ -f "${package_dir}/version" ]; then . "${package_dir}/version"; else echo_warning "Missing file: ${package_dir}/version"; fi
shell_include_files "${package_env_filename}" "${package_install_filename}"
load_conf 2>&1 >/dev/null
load_defaults 2>&1 >/dev/null
#if [ -f /root/mtwilson.env ]; then  . /root/mtwilson.env; fi

configure_privacyca_url() {

  local privacyca_server privacyca_url

  if [ -n "$PRIVACYCA_SERVER" ]; then
    privacyca_server="$PRIVACYCA_SERVER"
  elif [ -n "$MTWILSON_SERVER" ]; then
    privacyca_server="$MTWILSON_SERVER"
  else
    prompt_with_default privacyca_server "Privacy CA Server:" 127.0.0.1
  fi
  privacyca_url="https://${privacyca_server}:$DEFAULT_API_PORT/HisPrivacyCAWebServices2"
  update_property_in_file PrivacyCaUrl "${intel_conf_dir}/privacyca-client.properties" "${privacyca_url}"
  # Now prompt for the client files download username and password
  #local privacyca_download_username PRIVACYCA_DOWNLOAD_PASSWORD
  echo "You need to set a username and password for Trust Agents to download the Privacy CA client files."
  prompt_with_default PRIVACYCA_DOWNLOAD_USERNAME "PrivacyCA Administrator Username:" admin
  prompt_with_default_password PRIVACYCA_DOWNLOAD_PASSWORD "PrivacyCA Administrator Password:"
  export PRIVACYCA_DOWNLOAD_USERNAME="$PRIVACYCA_DOWNLOAD_USERNAME"
  export PRIVACYCA_DOWNLOAD_PASSWORD="$PRIVACYCA_DOWNLOAD_PASSWORD"
  PRIVACYCA_DOWNLOAD_PASSWORD_HASH=`mtwilson setup HashPassword --env-password=PRIVACYCA_DOWNLOAD_PASSWORD`
  update_property_in_file ClientFilesDownloadUsername "${intel_conf_dir}/PrivacyCA.properties" "${PRIVACYCA_DOWNLOAD_USERNAME}"
  update_property_in_file ClientFilesDownloadPassword "${intel_conf_dir}/PrivacyCA.properties" "${PRIVACYCA_DOWNLOAD_PASSWORD_HASH}"
}

setup_print_summary() {
  echo "Requirements summary:"
  if [ -n "$JAVA_HOME" ]; then
    echo "Java: $JAVA_VERSION"
  else
    echo "Java: not found"
  fi
  if using_glassfish; then
    if [ -n "$GLASSFISH_HOME" ]; then
      GLASSFISH_VERSION=`glassfish_version`
      echo "Glassfish: $GLASSFISH_VERSION"
    else
      echo "Glassfish: not found"
    fi
  fi
  if using_tomcat; then
    if [ -n "$TOMCAT_HOME" ]; then
      TOMCAT_VERSION=`tomcat_version`
      echo "Tomcat: $TOMCAT_VERSION"
    else
      echo "Tomcat: not found"
    fi
  fi
}

setup_interactive_install() {
  java_detect
  configure_privacyca_url
  create_privacyca_keys
  protect_privacyca_files
  if [ -n "$GLASSFISH_HOME" ]; then
    glassfish_running
    if [ -z "$GLASSFISH_RUNNING" ]; then
      glassfish_start_report
    fi
  elif [ -n "$TOMCAT_HOME" ]; then
    tomcat_running
    if [ -z "$TOMCAT_RUNNING" ]; then
      tomcat_start_report
    fi
  fi    

  if [ -n "$MTWILSON_SETUP_NODEPLOY" ]; then
    webservice_start_report "${webservice_application_name}"
  else
    webservice_uninstall "${webservice_application_name}"
    webservice_install "${webservice_application_name}" "${package_dir}"/HisPrivacyCAWebServices2.war
    
    if using_tomcat; then
       # Comment out listener in /var/lib/tomcat6/webapps/HisPrivacyCAWebServices2/WEB-INF/web.xml 
       echo "Waiting for PrivacyCA web.xml to finish deploying"
       while [ ! -f $TOMCAT_HOME/webapps/HisPrivacyCAWebServices2/WEB-INF/web.xml ]; do
       echo -n "." >> $INSTALL_LOG_FILE
       sleep 5
       done
      #sed -i.bak '/<listener>/,/<\/listener>/d' $TOMCAT_HOME/webapps/HisPrivacyCAWebServices2/WEB-INF/web.xml
    fi
    #webservice_running_report "${webservice_application_name}"
  fi
   #protect_privacyca_files
}



setup() {
#  mysql_clear; java_clear; glassfish_clear;
  mtwilson setup-env > "${package_env_filename}"
  . "${package_env_filename}"
#  if [[ -z "$JAVA_HOME" || -z "$GLASSFISH_HOME"  ]]; then
#      echo_warning "Missing one or more required packages"
#      setup_print_summary
#      exit 1
#  fi
  setup_interactive_install
}

# This function temporarily replaces /dev/random with /dev/urandom and 
# watches for the specified file... when the file exists then /dev/random
# is restored.
replace_random_until_exists() {
  markerfile=$1
  mv /dev/random /dev/random.old
  ln -s /dev/urandom /dev/random
  while [ ! -f "$markerfile" ]; do sleep 1; done
  rm /dev/random
  mv /dev/random.old /dev/random
  if [ ! -e /dev/random ]; then mknod -m 644 /dev/random c 1 8; chown root:root /dev/random; fi
  if [ ! -e /dev/urandom ]; then mknod -m 644 /dev/urandom c 1 9; chown root:root /dev/urandom; fi
}

create_privacyca_keys() {
  # if we already have keys, do not create new ones! because that will invalidate all registered trust agents
  if [ ! -f "${intel_conf_dir}/PrivacyCA.p12" ]; then
    # workaround for bug #568 - privacy ca takes 10-15 minutes to start first time, which is caused by key generation blocked due to not enough entropy in /dev/random
    # for production, you just need to wait the 10-15 minutes because you want secure keys. but for automated testing we benefit by getting this done in 1 second instead of 900 seconds
    if [[ "$PRIVACYCA_KEYGEN_URANDOM" == "yes" ]]; then
      replace_random_until_exists "${intel_conf_dir}/PrivacyCA.p12" &
    fi
    # creates PrivacyCA.p12 and endorsement.p12  (also creates the cacerts and clientfiles dirs)
    local oldpwd=`pwd`
    cd "${intel_conf_dir}"
    $java -jar ${package_dir}/HisPrivacyCAWebServices2-setup.jar
    cd "${oldpwd}"  
  fi
}

# The PrivacyCA creates PrivacyCA.p12 on start-up if it's missing; so we ensure it has safe permissions
protect_privacyca_files() {
  local PRIVACYCA_FILES="${intel_conf_dir}/PrivacyCA.p12 ${intel_conf_dir}/PrivacyCA.properties ${intel_conf_dir}/clientfiles ${intel_conf_dir}/clientfiles.zip ${intel_conf_dir}/cacerts"
  chmod 600 $PRIVACYCA_FILES
  if using_glassfish; then
    glassfish_permissions $PRIVACYCA_FILES
  elif using_tomcat; then
    tomcat_permissions $PRIVACYCA_FILES
  fi
}

RETVAL=0

# See how we were called.
case "$1" in
  version)
        echo "${package_name}"
  echo "Version ${VERSION:-Unknown}"
  echo "Build ${BUILD:-Unknown}"
        ;;
  start)
        webservice_start_report "${webservice_application_name}"
        protect_privacyca_files
        ;;
  stop)
        webservice_stop_report "${webservice_application_name}"
        ;;
  status)
        #if using_glassfish; then  
        #  glassfish_running_report
        #elif using_tomcat; then
        #  tomcat_running_report
        #fi

        webservice_running_report "${webservice_application_name}"
        ;;
  restart)
        webservice_stop_report "${webservice_application_name}"
        sleep 2
        webservice_start_report "${webservice_application_name}"
        ;;
  glassfish-restart)
        glassfish_restart
        ;;
  glassfish-stop)
        glassfish_shutdown
        ;;
  setup)
        setup
        ;;
  setup-env)
  # for sysadmin convenience
        mtwilson setup-env
        ;;
  setup-env-write)
  # for sysadmin convenience
        mtwilson setup-env > "${package_env_filename}"
  #echo "Saved environment in ${myenvdir}/${package_env_filename}"
        ;;
  edit)
        update_property_in_file "${2}" "${package_config_filename}" "${3}"
        ;;
  show)
        read_property_from_file "${2}" "${package_config_filename}"
        ;;
  uninstall)
        datestr=`date +%Y-%m-%d.%H%M`
        webservice_uninstall "${webservice_application_name}"
        if [ -f "${package_config_filename}" ]; then
          mkdir -p "${intel_conf_dir}"
          cp "${package_config_filename}" "${intel_conf_dir}"/${package_name}.properties.${datestr}
          echo "Saved configuration file in ${intel_conf_dir}/${package_name}.properties.${datestr}"
        fi
        # prevent disaster by ensuring that package_dir is inside /opt/intel
        if [[ "${package_dir}" == /opt/intel/* ]]; then
          rm -rf "${package_dir}"
        fi
  rm /usr/local/bin/${script_name}
        ;;
  help)
        echo "Usage: ${script_name} {setup|start|stop|status|uninstall}"
        ;;
  *)
        echo "Usage: ${script_name} {setup|start|stop|status|uninstall}"
        exit 1
esac

exit $RETVAL
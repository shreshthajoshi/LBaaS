package org.opendaylight.plugin2oc.neutron;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;

import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiPropertyBase;
import net.juniper.contrail.api.ObjectReference;
import net.juniper.contrail.api.types.InstanceIp;
import net.juniper.contrail.api.types.LoadbalancerMember;
import net.juniper.contrail.api.types.LoadbalancerMemberType;
import net.juniper.contrail.api.types.LoadbalancerPool;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.api.types.ServiceInstance;
import net.juniper.contrail.api.types.ServiceInstanceType;
import net.juniper.contrail.api.types.VirtualIp;
import net.juniper.contrail.api.types.VirtualMachineInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.controller.networkconfig.neutron.INeutronLoadBalancerListenerAware;
import org.opendaylight.controller.networkconfig.neutron.NeutronLoadBalancerListener;
import org.opendaylight.controller.networkconfig.neutron.NeutronLoadBalancerPoolMember;

public class ListernerHandler implements INeutronLoadBalancerListenerAware {
    /**
     * Logger instance.
     */
    static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancerPoolHandler.class);
    static ApiConnector apiConnector;

    @Override
    public int canCreateNeutronLoadBalancerListener(NeutronLoadBalancerListener loadBalancerListener) {

        if (loadBalancerListener == null) {
            LOGGER.error("LoadBalancerPool Member object can't be null..");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        apiConnector = Activator.apiConnector;
        if (loadBalancerListener.getLoadBalancerListenerTenantID() == null
                || loadBalancerListener.getNeutronLoadBalancerListenerLoadBalancerID() == null
                || loadBalancerListener.getNeutronLoadBalancerListenerDefaultPoolID() == null) {
            LOGGER.error("LoadBalancerPool Member TenanID/SubnetID can not be null");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        try {
            String loadBalancerListenerID = loadBalancerListener.getLoadBalancerListenerID();
            String loadBalancerVipID = loadBalancerListener.getNeutronLoadBalancerListenerLoadBalancerID();
            String loadBalancerPoolUUID = loadBalancerListener.getNeutronLoadBalancerListenerDefaultPoolID();
            String projectUUID = loadBalancerListener.getLoadBalancerListenerTenantID();
            try {
                if (!(loadBalancerListenerID.contains("-"))) {
                    loadBalancerListenerID = Utils.uuidFormater(loadBalancerListenerID);
                }
                if (!(loadBalancerVipID.contains("-"))) {
                    loadBalancerVipID = Utils.uuidFormater(loadBalancerVipID);
                }
                if (!(loadBalancerPoolUUID.contains("-"))) {
                    loadBalancerPoolUUID = Utils.uuidFormater(loadBalancerPoolUUID);
                }
                if (!(projectUUID.contains("-"))) {
                    projectUUID = Utils.uuidFormater(projectUUID);
                }
                boolean isValidloadBalancerListenerID = Utils.isValidHexNumber(loadBalancerListenerID);
                boolean isValidprojectUUID = Utils.isValidHexNumber(projectUUID);
                if (!isValidloadBalancerListenerID || !isValidprojectUUID) {
                    LOGGER.info("Badly formed Hexadecimal UUID...");
                    return HttpURLConnection.HTTP_BAD_REQUEST;
                }
                loadBalancerListenerID = UUID.fromString(loadBalancerListenerID).toString();
                loadBalancerVipID = UUID.fromString(loadBalancerVipID).toString();
                loadBalancerPoolUUID = UUID.fromString(loadBalancerPoolUUID).toString();
                projectUUID = UUID.fromString(projectUUID).toString();
            } catch (Exception ex) {
                LOGGER.error("UUID input incorrect", ex);
                return HttpURLConnection.HTTP_BAD_REQUEST;
            }
            Project project = (Project) apiConnector.findById(Project.class, projectUUID);
            if (project == null) {
                try {
                    Thread.currentThread();
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    LOGGER.error("InterruptedException :    ", e);
                    return HttpURLConnection.HTTP_BAD_REQUEST;
                }
                project = (Project) apiConnector.findById(Project.class, projectUUID);
                if (project == null) {
                    LOGGER.error("Could not find projectUUID...");
                    return HttpURLConnection.HTTP_NOT_FOUND;
                }
            }
            ServiceInstance serviceInstance = (ServiceInstance) apiConnector.findById(ServiceInstance.class,
                    loadBalancerListenerID);
            if (serviceInstance != null) {
                LOGGER.warn("Listener/Service Instance already exists with UUID" + loadBalancerListenerID);
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            LoadbalancerPool virtualLoadbalancerPool = (LoadbalancerPool) apiConnector.findById(LoadbalancerPool.class,
                    loadBalancerPoolUUID);
            if (virtualLoadbalancerPool == null) {
                LOGGER.warn("LoadbalancerPool does not exist");
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            VirtualIp virtualIP = (VirtualIp) apiConnector.findById(VirtualIp.class, loadBalancerVipID);
            if (virtualIP == null) {
                LOGGER.warn("VIP does not exist");
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            if (!(loadBalancerListener.getLoadBalancerListenerTenantID()
                    .equals(virtualLoadbalancerPool.getParentUuid()))) {
                LOGGER.warn("Listener with UUID: " + loadBalancerPoolUUID + "and Pool with UUID: "
                        + loadBalancerPoolUUID + " does not belong to same tenant");
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            return HttpURLConnection.HTTP_OK;
        } catch (IOException ie) {
            LOGGER.error("IOException :   " + ie);
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        } catch (Exception e) {
            LOGGER.error("Exception :   " + e);
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        }

    }

    @Override
    public void neutronLoadBalancerListenerCreated(NeutronLoadBalancerListener loadBalancerListener) {
        try {
            createLoadBalancerListener(loadBalancerListener);
        } catch (IOException ex) {
            LOGGER.warn("Exception  :    " + ex);
        }
        ServiceInstance serviceInstance = null;
        try {
            String loadBalancerListenerID = loadBalancerListener.getLoadBalancerListenerID();
            if (!(loadBalancerListenerID.contains("-"))) {
                loadBalancerListenerID = Utils.uuidFormater(loadBalancerListenerID);
            }
            loadBalancerListenerID = UUID.fromString(loadBalancerListenerID).toString();
            serviceInstance = (ServiceInstance) apiConnector.findById(LoadbalancerMember.class, loadBalancerListenerID);
            if (serviceInstance != null) {
                LOGGER.info("Service Instance creation verified ");
            }
        } catch (Exception e) {
            LOGGER.error("Exception :     " + e);
        }

    }

    private void createLoadBalancerListener(NeutronLoadBalancerListener loadBalancerListener) throws IOException {
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance = mapLoadBalancerListenerProperties(loadBalancerListener, serviceInstance);
        boolean loadBalancerListenerCreated;
        try {
            loadBalancerListenerCreated = apiConnector.create(serviceInstance);
            LOGGER.debug("serviceInstance:   " + loadBalancerListenerCreated);
            if (!loadBalancerListenerCreated) {
                LOGGER.info("loadBalancerListener creation failed..");
            }
        } catch (Exception Ex) {
            LOGGER.error("Exception : " + Ex);
        }
        LOGGER.info("Member having UUID " + loadBalancerListener.getLoadBalancerListenerID() + " sucessfully created");
    }

    @Override
    public int canUpdateNeutronLoadBalancerListener(NeutronLoadBalancerListener delta,
            NeutronLoadBalancerListener original) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void neutronLoadBalancerListenerUpdated(NeutronLoadBalancerListener loadBalancerListener) {
        // TODO Auto-generated method stub

    }

    @Override
    public int canDeleteNeutronLoadBalancerListener(NeutronLoadBalancerListener loadBalancerListener) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void neutronLoadBalancerListenerDeleted(NeutronLoadBalancerListener loadBalancerListener) {
        // TODO Auto-generated method stub

    }

    private ServiceInstance mapLoadBalancerListenerProperties(NeutronLoadBalancerListener loadBalancerListener,
            ServiceInstance serviceInstance) {
        String loadBalancerListenerID = loadBalancerListener.getLoadBalancerListenerID();
        String loadBalancerVipID = loadBalancerListener.getNeutronLoadBalancerListenerLoadBalancerID();
        String loadBalancerPoolUUID = loadBalancerListener.getNeutronLoadBalancerListenerDefaultPoolID();
        String projectUUID = loadBalancerListener.getLoadBalancerListenerTenantID();
        String listenerName = loadBalancerListener.getLoadBalancerListenerName();
        try {
            if (!(loadBalancerListenerID.contains("-"))) {
                loadBalancerListenerID = Utils.uuidFormater(loadBalancerListenerID);
            }
            loadBalancerListenerID = UUID.fromString(loadBalancerListenerID).toString();
            if (!(projectUUID.contains("-"))) {
                projectUUID = Utils.uuidFormater(projectUUID);
            }
            projectUUID = UUID.fromString(projectUUID).toString();
            Project project = (Project) apiConnector.findById(Project.class, projectUUID);
            serviceInstance.setParent(project);
        } catch (Exception ex) {
            LOGGER.error("UUID input incorrect", ex);
        }
        try {
            LoadbalancerPool virtualLoadbalancerPool = (LoadbalancerPool) apiConnector.findById(LoadbalancerPool.class,
                    loadBalancerPoolUUID);
            VirtualIp virtualIP = (VirtualIp) apiConnector.findById(VirtualIp.class, loadBalancerVipID);
        ServiceInstanceType service_instance_properties = new ServiceInstanceType();
        service_instance_properties.setLeftVirtualNetwork(loadBalancerPoolUUID);
//        service_instance_properties.setLeftIpAddress(virtualLoadbalancerPool.get);
        service_instance_properties.setRightVirtualNetwork(loadBalancerVipID);
//        service_instance_properties.setRightIpAddress(virtualIP.get);
        service_instance_properties.setAutoPolicy(true);
        serviceInstance.setProperties(service_instance_properties);
        serviceInstance.setName(listenerName);
        serviceInstance.setDisplayName(listenerName);
        serviceInstance.setUuid(loadBalancerListenerID);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return serviceInstance;
    }
}

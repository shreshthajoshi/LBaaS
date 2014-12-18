package org.opendaylight.plugin2oc.neutron;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;

import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ObjectReference;
import net.juniper.contrail.api.types.InstanceIp;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.api.types.ServiceInstance;
import net.juniper.contrail.api.types.ServiceInstanceType;
import net.juniper.contrail.api.types.ServiceTemplate;
import net.juniper.contrail.api.types.VirtualIp;
import net.juniper.contrail.api.types.VirtualIpType;
import net.juniper.contrail.api.types.VirtualMachineInterface;
import net.juniper.contrail.api.types.VirtualNetwork;
import net.juniper.contrail.api.types.VnSubnetsType;

import org.opendaylight.controller.networkconfig.neutron.INeutronLoadBalancerAware;
import org.opendaylight.controller.networkconfig.neutron.INeutronSubnetCRUD;
import org.opendaylight.controller.networkconfig.neutron.NeutronCRUDInterfaces;
import org.opendaylight.controller.networkconfig.neutron.NeutronLoadBalancer;
import org.opendaylight.controller.networkconfig.neutron.NeutronSubnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VIPHandler implements INeutronLoadBalancerAware {
    /**
     * Logger instance.
     */
    static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancerPoolHandler.class);
    static ApiConnector apiConnector;

    @Override
    public int canCreateNeutronLoadBalancer(NeutronLoadBalancer loadBalancer) {
        if (loadBalancer == null) {
            LOGGER.error("LoadBalancerPool Member object can't be null..");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        apiConnector = Activator.apiConnector;
        if (loadBalancer.getLoadBalancerTenantID() == null || loadBalancer.getLoadBalancerVipSubnetID() == null) {
            LOGGER.error("LoadBalancerPool Member TenanID/SubnetID can not be null");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        try {
            String loadBalancerID = loadBalancer.getLoadBalancerID();
            String loadBalancerVipSubnetID = loadBalancer.getLoadBalancerVipSubnetID();
            String projectUUID = loadBalancer.getLoadBalancerTenantID();
            try {
                if (!(loadBalancerID.contains("-"))) {
                    loadBalancerID = Utils.uuidFormater(loadBalancerID);
                }
                if (!(projectUUID.contains("-"))) {
                    projectUUID = Utils.uuidFormater(projectUUID);
                }
                if (!(loadBalancerVipSubnetID.contains("-"))) {
                    loadBalancerVipSubnetID = Utils.uuidFormater(loadBalancerVipSubnetID);
                }
                boolean isValidLoadBalancerID = Utils.isValidHexNumber(loadBalancerID);
                boolean isValidprojectUUID = Utils.isValidHexNumber(projectUUID);
                boolean isValidVipSubnetID = Utils.isValidHexNumber(loadBalancerVipSubnetID);
                if (!isValidLoadBalancerID || !isValidprojectUUID || !isValidVipSubnetID) {
                    LOGGER.info("Badly formed Hexadecimal UUID...");
                    return HttpURLConnection.HTTP_BAD_REQUEST;
                }
                projectUUID = UUID.fromString(projectUUID).toString();
                loadBalancerID = UUID.fromString(loadBalancerID).toString();
                loadBalancerVipSubnetID = UUID.fromString(loadBalancerVipSubnetID).toString();
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
            VirtualIp virtualVIP = (VirtualIp) apiConnector.findById(VirtualIp.class, loadBalancerID);
            if (virtualVIP != null) {
                LOGGER.warn("Loadbalancer already exists with UUID" + loadBalancerID);
                return HttpURLConnection.HTTP_FORBIDDEN;
            }

            /* to check if provided subnet ID already exists in contrail */

            INeutronSubnetCRUD subnetInterface = NeutronCRUDInterfaces.getINeutronSubnetCRUD(this);
            if (subnetInterface == null) {
                LOGGER.error("The subnet does not exists in ODL itself..");
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            NeutronSubnet subnet = subnetInterface.getSubnet(loadBalancerVipSubnetID);
            if (subnet == null) {
                LOGGER.error("Subnet does not exists...");
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            String networkUUID = subnet.getNetworkUUID();
            VirtualNetwork virtualnetwork = (VirtualNetwork) apiConnector.findById(VirtualNetwork.class, networkUUID);
            if (virtualnetwork == null) {
                LOGGER.error("No network exists for the specified subnet...");
                return HttpURLConnection.HTTP_FORBIDDEN;
            } else {
                try {
                    boolean ifSubnetExist = subnetExists(virtualnetwork.getNetworkIpam(), subnet);
                    if (!ifSubnetExist) {
                        LOGGER.error("The subnet does not exists..");
                        return HttpURLConnection.HTTP_FORBIDDEN;
                    }
                } catch (Exception e) {
                    LOGGER.error("Exception:  " + e);
                    return HttpURLConnection.HTTP_INTERNAL_ERROR;
                }
            }
            return HttpURLConnection.HTTP_OK;
        } catch (IOException ie) {
            LOGGER.error("IOException :   " + ie);
            System.out.println("exception 1");
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        } catch (Exception e) {
            LOGGER.error("Exception :   " + e);
            System.out.println("exception 2");
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        }

    }

    @Override
    public void neutronLoadBalancerCreated(NeutronLoadBalancer loadBalancer) {
        try {
            createLoadBalancer(loadBalancer);
        } catch (IOException ex) {
            LOGGER.warn("Exception  :    " + ex);
        }
        VirtualIp vip = null;
        try {
            String loadBalanceUUID = loadBalancer.getLoadBalancerID();
            if (!(loadBalanceUUID.contains("-"))) {
                loadBalanceUUID = Utils.uuidFormater(loadBalanceUUID);
            }
            loadBalanceUUID = UUID.fromString(loadBalanceUUID).toString();
            vip = (VirtualIp) apiConnector.findById(VirtualIp.class, loadBalanceUUID);
            if (vip != null) {
                LOGGER.info("Loadbalancer creation verified....");
            }
        } catch (Exception e) {
            LOGGER.error("Exception :     " + e);
        }
    }

    private void createLoadBalancer(NeutronLoadBalancer loadBalancer) throws IOException {
        VirtualIp virtualIP = new VirtualIp();
        virtualIP = mapLoadBalancerPoolProperties(loadBalancer, virtualIP);
        Project project = (Project) apiConnector.findById(Project.class, loadBalancer.getLoadBalancerTenantID());
        INeutronSubnetCRUD subnetInterface = NeutronCRUDInterfaces.getINeutronSubnetCRUD(this);
        NeutronSubnet subnet = subnetInterface.getSubnet(loadBalancer.getLoadBalancerVipSubnetID());
        String networkUUID = subnet.getNetworkUUID();
        VirtualNetwork virtualnetwork = (VirtualNetwork) apiConnector.findById(VirtualNetwork.class, networkUUID);
        System.out.println("virtualnetwork------" + virtualnetwork);
        VirtualMachineInterface vmi = new VirtualMachineInterface();
        vmi.setName(UUID.randomUUID().toString());
        vmi.setUuid(UUID.randomUUID().toString());
        vmi.setParent(project);
        vmi.setVirtualNetwork(virtualnetwork);
        boolean virtualMachineInterfaceCreated = apiConnector.create(vmi);
        if (!virtualMachineInterfaceCreated) {
            LOGGER.warn("actual virtualMachineInterface creation failed..");
        }
        /* setting vmi in Vip */
        virtualIP.setVirtualMachineInterface(vmi);
//        String ips = loadBalancer.getLoadBalancerVipAddress();
//        InstanceIp instanceIp = new InstanceIp();
//        String instaneIpUuid = UUID.randomUUID().toString();
//        instanceIp.setAddress(ips);
//        instanceIp.setName(instaneIpUuid);
//        instanceIp.setUuid(instaneIpUuid);
//        instanceIp.setParent(vmi);
//        instanceIp.setVirtualMachineInterface(vmi);
//        instanceIp.setVirtualNetwork(virtualnetwork);
//        boolean instanceIpCreated = apiConnector.create(instanceIp);
//        if (!instanceIpCreated) {
//            LOGGER.warn("instanceIp addition failed..");
//        }
//        LOGGER.info("Instance IP " + instanceIp.getAddress() + " added sucessfully...");
        boolean loadBalancerPoolCreated;
        try {
            loadBalancerPoolCreated = apiConnector.create(virtualIP);
            LOGGER.debug("loadBalancerPool:   " + loadBalancerPoolCreated);
            if (!loadBalancerPoolCreated) {
                LOGGER.info("loadBalancerPool creation failed..");
            }
            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setDisplayName(UUID.randomUUID().toString());
            serviceInstance.setName(UUID.randomUUID().toString());
            serviceInstance.setUuid(UUID.randomUUID().toString());
            ServiceTemplate serviceTemplate = new ServiceTemplate();
            serviceInstance.setServiceTemplate(serviceTemplate);
            ServiceInstanceType serviceInstanceType = new ServiceInstanceType();
            serviceInstanceType.setRightIpAddress(loadBalancer.getLoadBalancerVipAddress());
            serviceInstanceType.setRightVirtualNetwork(networkUUID);
            // serviceInstanceType.setLeftIpAddress(left_ip_address);
            // serviceInstanceType.setLeftVirtualNetwork(left_virtual_network);
            serviceInstance.setProperties(serviceInstanceType);
            Boolean createServiceInstance = apiConnector.create(serviceInstance);
            if (!createServiceInstance) {
                LOGGER.info("Service instance creation failed..");
            }
        } catch (Exception Ex) {
            LOGGER.error("Exception : " + Ex);
        }
    }

    @Override
    public int canUpdateNeutronLoadBalancer(NeutronLoadBalancer delta, NeutronLoadBalancer original) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void neutronLoadBalancerUpdated(NeutronLoadBalancer loadBalancer) {
        // TODO Auto-generated method stub

    }

    @Override
    public int canDeleteNeutronLoadBalancer(NeutronLoadBalancer loadBalancer) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void neutronLoadBalancerDeleted(NeutronLoadBalancer loadBalancer) {
        // TODO Auto-generated method stub

    }

    private VirtualIp mapLoadBalancerPoolProperties(NeutronLoadBalancer loadBalancer, VirtualIp virtualVIP) {
        String loadBalancerID = loadBalancer.getLoadBalancerID();
        String loadBalancerName = loadBalancer.getLoadBalancerName();
        String projectUUID = loadBalancer.getLoadBalancerTenantID();
        try {
            if (!(loadBalancerID.contains("-"))) {
                loadBalancerID = Utils.uuidFormater(loadBalancerID);
            }
            loadBalancerID = UUID.fromString(loadBalancerID).toString();
            if (!(projectUUID.contains("-"))) {
                projectUUID = Utils.uuidFormater(projectUUID);
            }
            projectUUID = UUID.fromString(projectUUID).toString();
            Project project = (Project) apiConnector.findById(Project.class, projectUUID);
            virtualVIP.setParent(project);
        } catch (Exception ex) {
            LOGGER.error("UUID input incorrect", ex);
        }
        VirtualIpType vipType = new VirtualIpType();
        vipType.setAddress(loadBalancer.getLoadBalancerVipAddress());
        vipType.setAdminState(true);
        vipType.setSubnetId(loadBalancer.getLoadBalancerVipSubnetID());
        virtualVIP.setDisplayName(loadBalancerName);
        virtualVIP.setUuid(loadBalancerID);
        // virtualVIP.setLoadbalancerPool(obj);

        return virtualVIP;
    }

    private boolean subnetExists(List<ObjectReference<VnSubnetsType>> ipamRefs, NeutronSubnet subnet) {
        if (ipamRefs != null) {
            for (ObjectReference<VnSubnetsType> ref : ipamRefs) {
                VnSubnetsType vnSubnetsType = ref.getAttr();
                if (vnSubnetsType != null) {
                    List<VnSubnetsType.IpamSubnetType> subnets = vnSubnetsType.getIpamSubnets();
                    if (subnets != null) {
                        for (VnSubnetsType.IpamSubnetType subnetValue : subnets) {
                            String[] ipPrefix = getIpPrefix(subnet);
                            Boolean doesSubnetExist = subnetValue.getSubnet().getIpPrefix().matches(ipPrefix[0]);
                            if (doesSubnetExist) {
                                return doesSubnetExist;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    String[] getIpPrefix(NeutronSubnet subnet) {
        String[] ipPrefix = null;
        String cidr = subnet.getCidr();
        if (cidr.contains("/")) {
            ipPrefix = cidr.split("/");
        } else {
            throw new IllegalArgumentException("String " + cidr + " not in correct format..");
        }
        return ipPrefix;
    }
}

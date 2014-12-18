package org.opendaylight.plugin2oc.neutron;

import java.util.List;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.UUID;

import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiPropertyBase;
import net.juniper.contrail.api.ObjectReference;
import net.juniper.contrail.api.types.InstanceIp;
import net.juniper.contrail.api.types.LoadbalancerMember;
import net.juniper.contrail.api.types.LoadbalancerMemberType;
import net.juniper.contrail.api.types.LoadbalancerPool;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.api.types.VirtualMachineInterface;

import org.opendaylight.controller.networkconfig.neutron.INeutronLoadBalancerPoolMemberAware;
import org.opendaylight.controller.networkconfig.neutron.NeutronLoadBalancerPoolMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle requests for Neutron LoadbalancerPool.
 */

public class LoadBalancerPoolMemberHandler implements INeutronLoadBalancerPoolMemberAware {
    /**
     * Logger instance.
     */
    static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancerPoolHandler.class);
    static ApiConnector apiConnector;

    @Override
    public int canCreateNeutronLoadBalancerPoolMember(NeutronLoadBalancerPoolMember loadBalancerPoolMember) {
        if (loadBalancerPoolMember == null) {
            LOGGER.error("LoadBalancerPool Member object can't be null..");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        apiConnector = Activator.apiConnector;
        if (loadBalancerPoolMember.getPoolMemberTenantID() == null
                || loadBalancerPoolMember.getPoolMemberSubnetID() == null) {
            LOGGER.error("LoadBalancerPool Member TenanID/SubnetID can not be null");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        try {
            String loadBalancerPoolID = loadBalancerPoolMember.getPoolID();
            String loadBalancerPoolMemberUUID = loadBalancerPoolMember.getPoolMemberID();
            String projectUUID = loadBalancerPoolMember.getPoolMemberTenantID();
            try {
                if (!(loadBalancerPoolMemberUUID.contains("-"))) {
                    loadBalancerPoolMemberUUID = Utils.uuidFormater(loadBalancerPoolMemberUUID);
                }
                if (!(projectUUID.contains("-"))) {
                    projectUUID = Utils.uuidFormater(projectUUID);
                }
                boolean isValidLoadBalancerPoolMemberUUID = Utils.isValidHexNumber(loadBalancerPoolMemberUUID);
                boolean isValidprojectUUID = Utils.isValidHexNumber(projectUUID);
                if (!isValidLoadBalancerPoolMemberUUID || !isValidprojectUUID) {
                    LOGGER.info("Badly formed Hexadecimal UUID...");
                    return HttpURLConnection.HTTP_BAD_REQUEST;
                }
                projectUUID = UUID.fromString(projectUUID).toString();
                loadBalancerPoolMemberUUID = UUID.fromString(loadBalancerPoolMemberUUID).toString();
                if (!(loadBalancerPoolID.contains("-"))) {
                    loadBalancerPoolID = Utils.uuidFormater(loadBalancerPoolID);
                }
                loadBalancerPoolID = UUID.fromString(loadBalancerPoolID).toString();
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
            if (project.getVirtualMachineInterfaces() != null) {
                List<ObjectReference<ApiPropertyBase>> vmiList = project.getVirtualMachineInterfaces();
                for (ObjectReference<ApiPropertyBase> ref : vmiList) {
                    String vmiUUID = ref.getUuid();
                    VirtualMachineInterface vmi = (VirtualMachineInterface) apiConnector.findById(
                            VirtualMachineInterface.class, vmiUUID);
                    // if (vmi.getVirtualMachine()==null) {
                    // LOGGER.warn("Virtual machine does not exists...");
                    // return HttpURLConnection.HTTP_FORBIDDEN;
                    // }
                    List<ObjectReference<ApiPropertyBase>> iip = vmi.getInstanceIpBackRefs();
                    for (ObjectReference<ApiPropertyBase> iipRef : iip) {
                        String iipUUID = iipRef.getUuid();
                        InstanceIp instanceIP = (InstanceIp) apiConnector.findById(InstanceIp.class, iipUUID);
                        if (!(loadBalancerPoolMember.getPoolMemberAddress().equals(instanceIP.getAddress()))) {
                            LOGGER.warn("LoadbalancerPool Member address does not exists...");
                            return HttpURLConnection.HTTP_FORBIDDEN;
                        }
                    }
                }
            } else {
                LOGGER.warn("No Servers available to create a member...");
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            LoadbalancerMember virtualLoadbalancerPoolMemberById = (LoadbalancerMember) apiConnector.findById(
                    LoadbalancerMember.class, loadBalancerPoolMemberUUID);
            if (virtualLoadbalancerPoolMemberById != null) {
                LOGGER.warn("LoadbalancerPool Member already exists with UUID" + loadBalancerPoolMemberUUID);
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            LoadbalancerPool virtualLoadbalancerPool = (LoadbalancerPool) apiConnector.findById(LoadbalancerPool.class,
                    loadBalancerPoolID);
            if (virtualLoadbalancerPool == null) {
                LOGGER.warn("LoadbalancerPool does not exist" + loadBalancerPoolID);
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            if (!(loadBalancerPoolMember.getPoolMemberTenantID().equals(virtualLoadbalancerPool.getParentUuid()))) {
                LOGGER.warn("Member with UUID: " + loadBalancerPoolID + "and Pool with UUID: " + loadBalancerPoolID
                        + " does not belong to same tenant");
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
    public void neutronLoadBalancerPoolMemberCreated(NeutronLoadBalancerPoolMember loadBalancerPoolMember) {
        try {
            createLoadBalancerMember(loadBalancerPoolMember);
        } catch (IOException ex) {
            LOGGER.warn("Exception  :    " + ex);
        }
        LoadbalancerMember loadbalancerMember = null;
        try {
            String loadBalancerPoolMemberUUID = loadBalancerPoolMember.getPoolMemberID();
            if (!(loadBalancerPoolMemberUUID.contains("-"))) {
                loadBalancerPoolMemberUUID = Utils.uuidFormater(loadBalancerPoolMemberUUID);
            }
            loadBalancerPoolMemberUUID = UUID.fromString(loadBalancerPoolMemberUUID).toString();
            loadbalancerMember = (LoadbalancerMember) apiConnector.findById(LoadbalancerMember.class,
                    loadBalancerPoolMemberUUID);
            if (loadbalancerMember != null) {
                LOGGER.info("LoadbalancerPool Member creation verified for Member with UUID--"
                        + loadBalancerPoolMemberUUID);
            }
        } catch (Exception e) {
            LOGGER.error("Exception :     " + e);
        }

    }

    private void createLoadBalancerMember(NeutronLoadBalancerPoolMember loadBalancerPoolMember) throws IOException {
        LoadbalancerMember virtualLoadBalancerMember = new LoadbalancerMember();
        virtualLoadBalancerMember = mapLoadBalancerMemberProperties(loadBalancerPoolMember, virtualLoadBalancerMember);
        boolean loadBalancerMemberCreated;
        try {
            loadBalancerMemberCreated = apiConnector.create(virtualLoadBalancerMember);
            LOGGER.debug("loadBalancerPool:   " + loadBalancerMemberCreated);
            if (!loadBalancerMemberCreated) {
                LOGGER.info("loadBalancerPool creation failed..");
            }
//            LoadbalancerPool lbp = (LoadbalancerPool) apiConnector.findById(LoadbalancerPool.class,
//                    loadBalancerPoolMember.getPoolID());
//            List<ObjectReference<ApiPropertyBase>> poolMemberList=lbp.getLoadbalancerMembers();
//            for (ObjectReference<ApiPropertyBase> ref : poolMemberList) {
//            }
//            boolean loadBalancerMemberupdate = apiConnector.update(lbp);
        } catch (Exception Ex) {
            LOGGER.error("Exception : " + Ex);
        }
        LOGGER.info("Member having UUID " + loadBalancerPoolMember.getPoolMemberID() + " sucessfully created");
    }

    @Override
    public int canUpdateNeutronLoadBalancerPoolMember(NeutronLoadBalancerPoolMember delta,
            NeutronLoadBalancerPoolMember original) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void neutronLoadBalancerPoolMemberUpdated(NeutronLoadBalancerPoolMember loadBalancerPoolMember) {
        // TODO Auto-generated method stub

    }

    @Override
    public int canDeleteNeutronLoadBalancerPoolMember(NeutronLoadBalancerPoolMember loadBalancerPoolMember) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void neutronLoadBalancerPoolMemberDeleted(NeutronLoadBalancerPoolMember loadBalancerPoolMember) {
        // TODO Auto-generated method stub

    }

    private LoadbalancerMember mapLoadBalancerMemberProperties(NeutronLoadBalancerPoolMember loadBalancerPoolMember,
            LoadbalancerMember virtualLoadBalancerMember) {
        String loadBalancerMemberUUID = loadBalancerPoolMember.getPoolMemberID();
        try {
            if (!(loadBalancerMemberUUID.contains("-"))) {
                loadBalancerMemberUUID = Utils.uuidFormater(loadBalancerMemberUUID);
            }
            loadBalancerMemberUUID = UUID.fromString(loadBalancerMemberUUID).toString();
            LoadbalancerPool lbp = (LoadbalancerPool) apiConnector.findById(LoadbalancerPool.class,
                    loadBalancerPoolMember.getPoolID());
            virtualLoadBalancerMember.setParent(lbp);
        } catch (Exception ex) {
            LOGGER.error("UUID input incorrect", ex);
        }
        LoadbalancerMemberType lbmType = new LoadbalancerMemberType();
        lbmType.setAddress(loadBalancerPoolMember.getPoolMemberAddress());
        lbmType.setProtocolPort(loadBalancerPoolMember.getPoolMemberProtoPort());
        if (loadBalancerPoolMember.getPoolMemberStatus() != null) {
            lbmType.setStatus(loadBalancerPoolMember.getPoolMemberStatus());
        }
        if (loadBalancerPoolMember.getPoolMemberWeight() != null) {
            lbmType.setWeight(loadBalancerPoolMember.getPoolMemberWeight());
        }
        if (loadBalancerPoolMember.getPoolMemberAdminStateIsUp() != null) {
            lbmType.setAdminState(loadBalancerPoolMember.getPoolMemberAdminStateIsUp());
        } else {
            lbmType.setAdminState(true);
        }
        virtualLoadBalancerMember.setProperties(lbmType);
        virtualLoadBalancerMember.setUuid(loadBalancerMemberUUID);
        virtualLoadBalancerMember.setName(loadBalancerMemberUUID);
        virtualLoadBalancerMember.setDisplayName(loadBalancerMemberUUID);
        return virtualLoadBalancerMember;
    }

}

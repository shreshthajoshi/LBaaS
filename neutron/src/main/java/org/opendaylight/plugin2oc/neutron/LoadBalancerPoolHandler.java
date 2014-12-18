package org.opendaylight.plugin2oc.neutron;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;

import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.types.LoadbalancerMember;
import net.juniper.contrail.api.types.LoadbalancerPool;
import net.juniper.contrail.api.types.LoadbalancerPoolType;
import net.juniper.contrail.api.types.Project;

import org.opendaylight.controller.networkconfig.neutron.INeutronLoadBalancerPoolAware;
import org.opendaylight.controller.networkconfig.neutron.NeutronLoadBalancerPool;
import org.opendaylight.controller.networkconfig.neutron.NeutronLoadBalancerPoolMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle requests for Neutron LoadbalancerPool.
 */

public class LoadBalancerPoolHandler implements INeutronLoadBalancerPoolAware {
    /**
     * Logger instance.
     */
    static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancerPoolHandler.class);
    static ApiConnector apiConnector;

    @Override
    public int canCreateNeutronLoadBalancerPool(NeutronLoadBalancerPool loadBalancerPool) {
        if (loadBalancerPool == null) {
            LOGGER.error("LoadBalancerPool object can't be null..");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        apiConnector = Activator.apiConnector;
        if (loadBalancerPool.getLoadBalancerPoolTenantID() == null) {
            LOGGER.error("LoadBalancerPool tenant Id can not be null");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        if (loadBalancerPool.getLoadBalancerPoolLbAlgorithm() == null) {
            LOGGER.error("LoadBalancerPool Algorithm can not be null");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        if (!(loadBalancerPool.getLoadBalancerPoolLbAlgorithm().equals("ROUND_ROBIN")
                || loadBalancerPool.getLoadBalancerPoolLbAlgorithm().equals("LEAST_CONNECTIONS") || loadBalancerPool
                .getLoadBalancerPoolLbAlgorithm().equals("Source IP"))) {
            LOGGER.error("LoadBalancerPool Algorithm can not be anything other than ROUND_ROBIN and LEAST_CONNECTIONS and Source IP");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        if (loadBalancerPool.getLoadBalancerPoolProtocol() == null) {
            LOGGER.error("LoadBalancerPool protocol can not be null");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        if (!(loadBalancerPool.getLoadBalancerPoolProtocol().equals("TCP")
                || loadBalancerPool.getLoadBalancerPoolProtocol().equals("HTTP") || loadBalancerPool
                .getLoadBalancerPoolProtocol().equals("HTTPS"))) {
            LOGGER.error("LoadBalancerPool Protocol can not be other than TCP/HTTP/HTTPS");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        if (loadBalancerPool.getLoadBalancerPoolMembers() != null) {
            List<NeutronLoadBalancerPoolMember> i = loadBalancerPool.getLoadBalancerPoolMembers();
            for (NeutronLoadBalancerPoolMember ref : i) {
                String poolmemberID = ref.getPoolMemberID();
                String tenantID = ref.getPoolMemberTenantID();
                if (!(tenantID.equals(loadBalancerPool.getLoadBalancerPoolTenantID()))) {
                    LOGGER.error("Member and pool does not belong to same tenant");
                    return HttpURLConnection.HTTP_BAD_REQUEST;
                }
                try {
                    LoadbalancerMember lbpm = (LoadbalancerMember) apiConnector.findById(LoadbalancerMember.class,
                            poolmemberID);
                    if (lbpm != null) {
                        LOGGER.error("Member already exist with UUID: " + poolmemberID);
                        return HttpURLConnection.HTTP_BAD_REQUEST;
                    }
                } catch (IOException e) {
                    LOGGER.error("IOException :   " + e);
                    return HttpURLConnection.HTTP_INTERNAL_ERROR;
                }
            }
        }
        try {
            String loadBalancerPoolUUID = loadBalancerPool.getLoadBalancerPoolID();
            String projectUUID = loadBalancerPool.getLoadBalancerPoolTenantID();
            try {
                if (!(loadBalancerPoolUUID.contains("-"))) {
                    loadBalancerPoolUUID = Utils.uuidFormater(loadBalancerPoolUUID);
                }
                if (!(projectUUID.contains("-"))) {
                    projectUUID = Utils.uuidFormater(projectUUID);
                }
                boolean isValidLoadBalancerPoolUUID = Utils.isValidHexNumber(loadBalancerPoolUUID);
                boolean isValidprojectUUID = Utils.isValidHexNumber(projectUUID);
                if (!isValidLoadBalancerPoolUUID || !isValidprojectUUID) {
                    LOGGER.info("Badly formed Hexadecimal UUID...");
                    return HttpURLConnection.HTTP_BAD_REQUEST;
                }
                projectUUID = UUID.fromString(projectUUID).toString();
                loadBalancerPoolUUID = UUID.fromString(loadBalancerPoolUUID).toString();
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
            String virtualLoadbalancerPoolByName = apiConnector.findByName(LoadbalancerPool.class, project,
                    loadBalancerPool.getLoadBalancerPoolName());
            if (virtualLoadbalancerPoolByName != null) {
                LOGGER.warn("POOL already exists with name : " + virtualLoadbalancerPoolByName);
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            LoadbalancerPool virtualLoadbalancerPoolById = (LoadbalancerPool) apiConnector.findById(
                    LoadbalancerPool.class, loadBalancerPoolUUID);
            if (virtualLoadbalancerPoolById != null) {
                LOGGER.warn("LoadbalancerPool already exists with UUID" + loadBalancerPoolUUID);
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
    public void neutronLoadBalancerPoolCreated(NeutronLoadBalancerPool loadBalancerPool) {
        try {
            createLoadBalancerPool(loadBalancerPool);
        } catch (IOException ex) {
            LOGGER.warn("Exception  :    " + ex);
        }
        LoadbalancerPool loadbalancerPool = null;
        try {
            String loadBalancerPoolUUID = loadBalancerPool.getLoadBalancerPoolID();
            if (!(loadBalancerPoolUUID.contains("-"))) {
                loadBalancerPoolUUID = Utils.uuidFormater(loadBalancerPoolUUID);
            }
            loadBalancerPoolUUID = UUID.fromString(loadBalancerPoolUUID).toString();
            loadbalancerPool = (LoadbalancerPool) apiConnector.findById(LoadbalancerPool.class, loadBalancerPoolUUID);
            if (loadbalancerPool != null) {
                LOGGER.info("LoadbalancerPool creation verified....");
            } else {
                LOGGER.info("LoadbalancerPool creation failed...");
            }
        } catch (Exception e) {
            LOGGER.error("Exception :     " + e);
        }
    }

    private void createLoadBalancerPool(NeutronLoadBalancerPool loadBalancerPool) throws IOException {
        LoadbalancerPool virtualLoadBalancerPool = new LoadbalancerPool();
        virtualLoadBalancerPool = mapLoadBalancerPoolProperties(loadBalancerPool, virtualLoadBalancerPool);
        boolean loadBalancerPoolCreated;
        try {
            loadBalancerPoolCreated = apiConnector.create(virtualLoadBalancerPool);
            LOGGER.debug("loadBalancerPool:   " + loadBalancerPoolCreated);
            if (!loadBalancerPoolCreated) {
                LOGGER.info("loadBalancerPool creation failed..");
            }
        } catch (Exception Ex) {
            LOGGER.error("Exception : " + Ex);
        }
        LOGGER.info("loadBalancerPool:" + loadBalancerPool.getLoadBalancerPoolName() + "having ID"
                + loadBalancerPool.getLoadBalancerPoolID() + "succesfully created.");
        if (loadBalancerPool.getLoadBalancerPoolMembers() != null) {
            List<NeutronLoadBalancerPoolMember> i = loadBalancerPool.getLoadBalancerPoolMembers();
            for (NeutronLoadBalancerPoolMember ref : i) {
                LoadBalancerPoolMemberHandler lbmh = new LoadBalancerPoolMemberHandler();
                int value = lbmh.canCreateNeutronLoadBalancerPoolMember(ref);
                if (value == 200) {
                    lbmh.neutronLoadBalancerPoolMemberCreated(ref);
                } else {
                    LOGGER.error("NeutronLoadBalancerPool Member creation failed");
                }
            }
        }
    }

    @Override
    public int canUpdateNeutronLoadBalancerPool(NeutronLoadBalancerPool delta, NeutronLoadBalancerPool original) {
        apiConnector = Activator.apiConnector;
        LoadbalancerPool virtualLoadBalancerPool;
        if (delta == null || original == null) {
            LOGGER.error("NeutronLoadBalancerPool objects cant be empty or null");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        if (delta.getLoadBalancerPoolLbAlgorithm() != null) {
            LOGGER.error("NeutronLoadBalancerPool Algorithm cant be updated");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        String loadBalancerPoolUUID = original.getLoadBalancerPoolID();
        try {
            if (!(loadBalancerPoolUUID.contains("-"))) {
                loadBalancerPoolUUID = Utils.uuidFormater(loadBalancerPoolUUID);
            }
            loadBalancerPoolUUID = UUID.fromString(loadBalancerPoolUUID).toString();
        } catch (Exception ex) {
            LOGGER.error("UUID input incorrect", ex);
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        try {
            virtualLoadBalancerPool = (LoadbalancerPool) apiConnector.findById(LoadbalancerPool.class,
                    loadBalancerPoolUUID);
        } catch (IOException e) {
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        }
        if (virtualLoadBalancerPool == null) {
            LOGGER.error("No LoadbalancerPool exists for the specified ID...");
            return HttpURLConnection.HTTP_FORBIDDEN;
        }
        return HttpURLConnection.HTTP_OK;
    }

    @Override
    public void neutronLoadBalancerPoolUpdated(NeutronLoadBalancerPool loadBalancerPool) {
        String loadBalancerPoolUUID = loadBalancerPool.getLoadBalancerPoolID();
        try {
            if (!(loadBalancerPoolUUID.contains("-"))) {
                loadBalancerPoolUUID = Utils.uuidFormater(loadBalancerPoolUUID);
            }
            loadBalancerPoolUUID = UUID.fromString(loadBalancerPoolUUID).toString();
        } catch (Exception ex) {
            LOGGER.error("UUID input incorrect", ex);
        }
        try {
            updateLoadBalancerPool(loadBalancerPool);
            // LoadbalancerPool virtualLoadBalancerPool = (LoadbalancerPool)
            // apiConnector.findById(LoadbalancerPool.class,
            // loadBalancerPoolUUID);
            // if(){
            //
            // }
        } catch (IOException e) {
            LOGGER.error("Exception: " + e);
        }

    }

    private void updateLoadBalancerPool(NeutronLoadBalancerPool loadBalancerPool) throws IOException {

    }

    @Override
    public int canDeleteNeutronLoadBalancerPool(NeutronLoadBalancerPool loadBalancerPool) {
        apiConnector = Activator.apiConnector;
        LoadbalancerPool virtualLoadBalancerPool = null;
        String loadBalancerPoolUUID = loadBalancerPool.getLoadBalancerPoolID();
        try {
            if (!(loadBalancerPoolUUID.contains("-"))) {
                loadBalancerPoolUUID = Utils.uuidFormater(loadBalancerPoolUUID);
            }
            loadBalancerPoolUUID = UUID.fromString(loadBalancerPoolUUID).toString();
        } catch (Exception ex) {
            LOGGER.error("UUID input incorrect", ex);
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        try {
            virtualLoadBalancerPool = (LoadbalancerPool) apiConnector.findById(LoadbalancerPool.class,
                    loadBalancerPoolUUID);
            if (virtualLoadBalancerPool != null) {
                if (virtualLoadBalancerPool.getVirtualIpBackRefs() != null) {
                    LOGGER.error("LoadbalancerPool has VIP associated with it");
                    return HttpURLConnection.HTTP_FORBIDDEN;
                }
                if (virtualLoadBalancerPool.getLoadbalancerMembers() != null) {
                    LOGGER.error("LoadbalancerPool has members associated with it");
                    return HttpURLConnection.HTTP_FORBIDDEN;
                }
                return HttpURLConnection.HTTP_OK;
            } else {
                LOGGER.info("No LoadbalancerPool exists with ID :  " + loadBalancerPoolUUID);
                return HttpURLConnection.HTTP_BAD_REQUEST;
            }
        } catch (Exception e) {
            LOGGER.error("Exception : " + e);
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        }
    }

    @Override
    public void neutronLoadBalancerPoolDeleted(NeutronLoadBalancerPool loadBalancerPool) {
        LoadbalancerPool virtualLoadBalancerPool = null;
        try {
            String loadBalancerPoolUUID = loadBalancerPool.getLoadBalancerPoolID();
            if (!(loadBalancerPoolUUID.contains("-"))) {
                loadBalancerPoolUUID = Utils.uuidFormater(loadBalancerPoolUUID);
            }
            loadBalancerPoolUUID = UUID.fromString(loadBalancerPoolUUID).toString();
            virtualLoadBalancerPool = (LoadbalancerPool) apiConnector.findById(LoadbalancerPool.class,
                    loadBalancerPoolUUID);
            apiConnector.delete(virtualLoadBalancerPool);
            if (virtualLoadBalancerPool == null) {
                LOGGER.info("LoadbalancerPool deletion verified....");
            } else {
                LOGGER.info("LoadbalancerPool with ID :  " + loadBalancerPoolUUID + "deletion failed");
            }
        } catch (Exception ex) {
            LOGGER.error("Exception :   " + ex);
        }

    }

    private LoadbalancerPool mapLoadBalancerPoolProperties(NeutronLoadBalancerPool loadBalancerPool,
            LoadbalancerPool virtualLoadBalancerPool) {
        String loadBalancerPoolUUID = loadBalancerPool.getLoadBalancerPoolID();
        String loadBalancerPoolName = loadBalancerPool.getLoadBalancerPoolName();
        String projectUUID = loadBalancerPool.getLoadBalancerPoolTenantID();
        try {
            if (!(loadBalancerPoolUUID.contains("-"))) {
                loadBalancerPoolUUID = Utils.uuidFormater(loadBalancerPoolUUID);
            }
            loadBalancerPoolUUID = UUID.fromString(loadBalancerPoolUUID).toString();
            if (!(projectUUID.contains("-"))) {
                projectUUID = Utils.uuidFormater(projectUUID);
            }
            projectUUID = UUID.fromString(projectUUID).toString();
            Project project = (Project) apiConnector.findById(Project.class, projectUUID);
            virtualLoadBalancerPool.setParent(project);
        } catch (Exception ex) {
            LOGGER.error("UUID input incorrect", ex);
        }
        LoadbalancerPoolType loadbalancer_pool_properties = new LoadbalancerPoolType();
        loadbalancer_pool_properties.setLoadbalancerMethod(loadBalancerPool.getLoadBalancerPoolLbAlgorithm());
        loadbalancer_pool_properties.setProtocol(loadBalancerPool.getLoadBalancerPoolProtocol());
        // loadbalancer_pool_properties.setSubnetId("6164c302-f074-4164-b522-bfa150090392");
        if (loadBalancerPool.getLoadBalancerPoolAdminIsStateIsUp() != null) {
            loadbalancer_pool_properties.setAdminState(loadBalancerPool.getLoadBalancerPoolAdminIsStateIsUp());
        } else {
            loadbalancer_pool_properties.setAdminState(true);
        }
        if (loadBalancerPool.getLoadBalancerPoolStatus() != null) {
            loadbalancer_pool_properties.setStatus(loadBalancerPool.getLoadBalancerPoolStatus());
        }
        if (loadBalancerPool.getLoadBalancerPoolDescription() != null) {
            loadbalancer_pool_properties.setStatusDescription(loadBalancerPool.getLoadBalancerPoolDescription());
        }
        virtualLoadBalancerPool.setUuid(loadBalancerPoolUUID);
        virtualLoadBalancerPool.setName(loadBalancerPoolName);
        virtualLoadBalancerPool.setDisplayName(loadBalancerPoolName);
        virtualLoadBalancerPool.setProperties(loadbalancer_pool_properties);
        /* haproxy is the provider for loadbalancer pool in OpenContrail */
        virtualLoadBalancerPool.setProvider("haproxy");
        return virtualLoadBalancerPool;
    }

}

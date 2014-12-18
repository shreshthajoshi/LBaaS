package org.opendaylight.plugin2oc.neutron;

import org.opendaylight.controller.networkconfig.neutron.INeutronLoadBalancerHealthMonitorAware;
import org.opendaylight.controller.networkconfig.neutron.NeutronLoadBalancerHealthMonitor;

public class LoadBalancerHealthMonitorHandler implements INeutronLoadBalancerHealthMonitorAware {

    @Override
    public int canCreateNeutronLoadBalancerHealthMonitor(NeutronLoadBalancerHealthMonitor loadBalancerHealthMonitor) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void neutronLoadBalancerHealthMonitorCreated(NeutronLoadBalancerHealthMonitor loadBalancerHealthMonitor) {
        // TODO Auto-generated method stub

    }

    @Override
    public int canUpdateNeutronLoadBalancerHealthMonitor(NeutronLoadBalancerHealthMonitor delta,
            NeutronLoadBalancerHealthMonitor original) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void neutronLoadBalancerHealthMonitorUpdated(NeutronLoadBalancerHealthMonitor loadBalancerHealthMonitor) {
        // TODO Auto-generated method stub

    }

    @Override
    public int canDeleteNeutronLoadBalancerHealthMonitor(NeutronLoadBalancerHealthMonitor loadBalancerHealthMonitor) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void neutronLoadBalancerHealthMonitorDeleted(NeutronLoadBalancerHealthMonitor loadBalancerHealthMonitor) {
        // TODO Auto-generated method stub

    }

}

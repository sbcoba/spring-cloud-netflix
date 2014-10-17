package org.springframework.cloud.netflix.ribbon.eureka;

import static com.netflix.client.config.CommonClientConfigKey.DeploymentContextBasedVipAddresses;
import static com.netflix.client.config.CommonClientConfigKey.EnableZoneAffinity;
import static com.netflix.client.config.CommonClientConfigKey.NFLoadBalancerRuleClassName;
import static com.netflix.client.config.CommonClientConfigKey.NIWSServerListClassName;
import static com.netflix.client.config.CommonClientConfigKey.NIWSServerListFilterClassName;

import org.springframework.cloud.netflix.ribbon.ServerListInitializer;

import com.netflix.client.ClientFactory;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext.ContextKey;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ZoneAvoidanceRule;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;

/**
 * Convenience class that sets up some configuration defaults for eureka-discovered ribbon
 * clients.
 * 
 * @author Spencer Gibb
 * @author Dave Syer
 */
public class EurekaRibbonInitializer implements ServerListInitializer {

	private EurekaClientConfig client;

	public EurekaRibbonInitializer(EurekaClientConfig client) {
		this.client = client;
	}

	@Override
	public void initialize(String serviceId) {
		if (client != null
				&& ConfigurationManager.getDeploymentContext().getValue(ContextKey.zone) == null) {
			String[] zones = client.getAvailabilityZones(client.getRegion());
			String zone = zones != null && zones.length > 0 ? zones[0] : null;
			if (zone != null) {
				// You can set this with archaius.deployment.* (maybe requires
				// custom deployment context)?
				ConfigurationManager.getDeploymentContext().setValue(ContextKey.zone,
						zone);
			}
		}
		// TODO: should this look more like hibernate spring boot props?
		// TODO: only set the property if it hasn't already been set?
		setProp(serviceId, NIWSServerListClassName.key(),
				DiscoveryEnabledNIWSServerList.class.getName());
		// FIXME: what should this be?
		setProp(serviceId, DeploymentContextBasedVipAddresses.key(), serviceId);
		setProp(serviceId, NFLoadBalancerRuleClassName.key(),
				ZoneAvoidanceRule.class.getName());
		setProp(serviceId, NIWSServerListFilterClassName.key(),
				ZonePreferenceServerListFilter.class.getName());
		setProp(serviceId, EnableZoneAffinity.key(), "true");
		ILoadBalancer loadBalancer = ClientFactory.getNamedLoadBalancer(serviceId);
		wrapServerList(loadBalancer);
	}

	private void wrapServerList(ILoadBalancer balancer) {
		if (balancer instanceof DynamicServerListLoadBalancer) {
			@SuppressWarnings("unchecked")
			DynamicServerListLoadBalancer<Server> dynamic = (DynamicServerListLoadBalancer<Server>) balancer;
			ServerList<Server> list = dynamic.getServerListImpl();
			if (!(list instanceof DomainExtractingServerList)) {
				// This is optional: you can use the native Eureka AWS features as long as
				// the server zone is populated. TODO: find a way to back off if AWS
				// metadata *is* available.
				dynamic.setServerListImpl(new DomainExtractingServerList(list));
			}
		}
	}

	protected void setProp(String serviceId, String suffix, String value) {
		// how to set the namespace properly?
		String namespace = "ribbon";
		ConfigurationManager.getConfigInstance().setProperty(
				serviceId + "." + namespace + "." + suffix, value);
	}

}
/*******************************************************************************
 * Copyright (c) 2014-2015 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.openshift.internal.restclient.model;

import static com.openshift.internal.restclient.capability.CapabilityInitializer.initializeCapabilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import com.openshift.restclient.IClient;
import com.openshift.restclient.model.IContainer;
import com.openshift.restclient.model.IPod;
import com.openshift.restclient.model.IPort;

/**
 * @author Jeff Cantrill
 */
public class Pod extends KubernetesResource implements IPod {

	private static final String POD_IP = "status.podIP";
	private static final String POD_HOST = "status.hostIP";
	private static final String POD_CONTAINERS = "spec.containers";
	private static final String POD_DELETION_TIMESTAMP = "metadata.deletionTimestamp";
	
	private static final String POD_STATUS_PHASE = "status.phase";
	private static final String POD_STATUS_REASON = "status.reason";
	private static final String POD_STATUS_CONTAINER_STATUSES = "status.containerStatuses";
	
	// container reasons fields and corresponding status prefixes
	private static final Map<String, String> POD_STATUS_CONTAINER_STATES = new HashMap<String, String>() {{
		put("state.waiting.reason", "");
		put("state.terminated.reason", "");
		put("state.terminated.signal", "Signal: ");
		put("state.terminated.exitCode", "Exit Code: ");		
	}};

	public Pod(ModelNode node, IClient client, Map<String, String []> propertyKeys) {
		super(node, client, propertyKeys);
		initializeCapabilities(getModifiableCapabilities(), this, client);
	}

	@Override
	public String getIP() {
		return asString(POD_IP);
	}

	@Override
	public String getHost() {
		return asString(POD_HOST);
	}

	@Override
	public Collection<String> getImages() {
		Collection<String> images = new ArrayList<String>();
		ModelNode node = get(POD_CONTAINERS);
		if(node.getType() != ModelType.LIST) return images;
		for (ModelNode entry : node.asList()) {
			images.add(entry.get("image").asString());
		}
		return images;
	}
	
	/**
	 * The logic of the method is a copied from 'podStatus' function of
	 * [app/scripts/filters/resources.js] of [openshift/origin-web-console]
	 */

	@Override
	public String getStatus() {
		if (get(POD_DELETION_TIMESTAMP).isDefined()) {
			return "Terminating";
		}
		ModelNode node = get(POD_STATUS_CONTAINER_STATUSES);
		if (node.getType() == ModelType.LIST) {
			for (ModelNode containerStatus : node.asList()) {
				for (String containerReason: POD_STATUS_CONTAINER_STATES.keySet()) {
					String status = getContainerStatusIfDefined(
							containerStatus, containerReason, POD_STATUS_CONTAINER_STATES.get(containerReason));
					if (status != null) {
						return status;
					}
				}
			}
		}
		return get(POD_STATUS_REASON).isDefined() ? asString(POD_STATUS_REASON) : asString(POD_STATUS_PHASE);
	}
	
	private String getContainerStatusIfDefined(ModelNode containerStatus, String path, String statusPrefix) {
		ModelNode node = containerStatus.get(getPath(path));
		if (node.isDefined()) {
			return statusPrefix + node.asString();
		}
		return null;
	}

	@Override
	public Set<IPort> getContainerPorts() {
		Set<IPort> ports = new HashSet<IPort>();
		ModelNode node = get(POD_CONTAINERS);
		if(node.getType() == ModelType.LIST) {
			for (ModelNode container : node.asList()) {
				ModelNode containerPorts = container.get(getPath(PORTS));
				if(containerPorts.getType() == ModelType.LIST) {
					for (ModelNode portNode : containerPorts.asList()) {
						ports.add(new Port(portNode));
					}
				}
			}
		}
		return Collections.unmodifiableSet(ports);
	}

	@Override
	public IContainer addContainer(String name) {
		ModelNode containers = get(POD_CONTAINERS);
		Container container = new Container(containers.add());
		container.setName(name);
		return container;
	}

	@Override
	public Collection<IContainer> getContainers() {
		ModelNode containers = get(POD_CONTAINERS);
		if(containers.isDefined() && ModelType.LIST == containers.getType()) {
			return containers.asList().stream().map(n->new Container(n, getPropertyKeys())).collect(Collectors.toList());
		}
		return Collections.emptyList();
	}
	
	
	
	
}

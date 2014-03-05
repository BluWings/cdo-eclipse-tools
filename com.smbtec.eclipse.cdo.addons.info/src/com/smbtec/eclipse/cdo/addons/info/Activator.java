/*******************************************************************************
 * Copyright (c) 2014 SMB Gesellschaft fuer Softwareentwicklung mbH
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Lars Martin - initial API and implementation
 *******************************************************************************/
package com.smbtec.eclipse.cdo.addons.info;

import java.util.Collection;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import com.buschmais.cdo.api.CdoManager;
import com.buschmais.cdo.api.CdoManagerFactory;
import com.buschmais.cdo.api.annotation.PostCreate;
import com.buschmais.cdo.api.annotation.PostDelete;
import com.buschmais.cdo.api.annotation.PostUpdate;

/**
 * The {@link Activator} controls the plug-in's life cycle.
 *
 * <p>
 * <b>Note:</b> Currently only one {@link CdoManager} will be bound to this instance: first come, first serve.
 * </p>
 */
public class Activator extends AbstractUIPlugin {

    /**
     * The Plug-in ID.
     */
    public static final String ID_PLUGIN = "com.smbtec.eclipse.cdo.addons.info"; //$NON-NLS-1$

    /**
     * Shared static instance.
     */
    private static Activator instance;

    /**
     * The lock object to synchronize access to {@code updateNeeded} flag.
     */
    private final Object lock = new Object();

    /**
     * Indicates that an (UI) update is needed.
     */
    private boolean updateNeeded = true;

    /**
     * The listener receives service notifications for {@link CdoManagerFactory} services.
     */
    private ServiceListener listener;

    private CdoManagerFactory cdoManagerFactory;

    private CdoManager cdoManager;

    /**
     * Ctor.
     */
    public Activator() {
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);
        instance = this;
        listener = new ServiceListener() {

            @Override
            public void serviceChanged(final ServiceEvent event) {
                ServiceReference<?> reference = event.getServiceReference();
                switch (event.getType()) {
                    case ServiceEvent.REGISTERED:
                        // only the first CdoManagerFactory service will be used
                        if (cdoManagerFactory == null) {
                            cdoManagerFactory = (CdoManagerFactory) context.getService(reference);
                            cdoManager = cdoManagerFactory.createCdoManager();
                            // register this plugin as instance listener to receive notifications
                            cdoManager.registerInstanceListener(this);
                        } else {
                            getLog().log(new Status(IStatus.WARNING, ID_PLUGIN, "CdoManager already bound"));
                        }
                        break;
                    case ServiceEvent.UNREGISTERING:
                        if (cdoManager != null) {
                            cdoManager.close();
                            cdoManager = null;
                            cdoManagerFactory = null;
                        }
                        break;
                    default:
                        break;
                }
            }

        };
        try {
            String filter = "(objectclass=" + CdoManagerFactory.class.getName() + ")"; //$NON-NLS-1$, $NON-NLS-2$
            context.addServiceListener(listener, filter);
            Collection<ServiceReference<CdoManagerFactory>> references = context.getServiceReferences(CdoManagerFactory.class, null);
            for (ServiceReference<CdoManagerFactory> reference : references) {
                listener.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, reference));
            }
        } catch (InvalidSyntaxException e) {
            Activator.getInstance().getLog().log(new Status(IStatus.ERROR, ID_PLUGIN, "Error while registering services listener", e));
        }
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        if (cdoManager != null) {
            cdoManager.close();
            cdoManager = null;
            cdoManagerFactory = null;
        }
        instance = null;
        super.stop(context);
    }

    /**
     * Gets the shared instance.
     */
    public static Activator getInstance() {
        return instance;
    }

    /**
     * Gets the {@link CdoManager} bound to this instance. Will be {@code null} if no {@link CdoManagerFactory} was registered before or an
     * error occurred.
     */
    protected CdoManager getCdoManager() {
        return cdoManager;
    }

    /**
     * Receives database notifications and sets the flag to trigger the worker thread.
     */
    @PostCreate
    @PostDelete
    @PostUpdate
    public void update() {
        triggerUpdate();
    }

    /*
     * synchronized access to updateNeeded field
     */
    protected void triggerUpdate() {
        synchronized (lock) {
            this.updateNeeded = true;
        }
    }

    /*
     * synchronized access to updateNeeded field
     */
    protected boolean updateNeeded() {
        synchronized (lock) {
            boolean result = this.updateNeeded;
            this.updateNeeded = false;
            return result;
        }
    }

}

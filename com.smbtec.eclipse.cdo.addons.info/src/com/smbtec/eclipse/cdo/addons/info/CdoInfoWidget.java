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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.menus.WorkbenchWindowControlContribution;

import com.buschmais.cdo.api.CdoException;
import com.buschmais.cdo.api.CdoManager;
import com.buschmais.cdo.api.Query.Result;
import com.buschmais.cdo.api.Query.Result.CompositeRowObject;

/**
 * <p>
 * Widget contribution to show node and relationship count in status bar on the bottom right-hand side of your workbench window.
 * </p>
 * <p>
 * The worker thread will check every two seconds if an update notification has been received. So expensive database calls to get the node
 * and relationship count will only be made if and only if a notification has been received previously.
 * </p>
 * <p>
 * TODO: Notification listener and worker thread implementation should be refactored to ensure reusability.
 * </p>
 */
public class CdoInfoWidget extends WorkbenchWindowControlContribution implements Runnable {

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    private static final String countQuery = "MATCH (n)-[r]-() RETURN COUNT(DISTINCT n) AS nodes, COUNT(r) AS rels";

    protected Display display;

    /**
     * The Label to visualize database information.
     */
    protected Label infoLabel;

    @Override
    protected Control createControl(final Composite parent) {
        display = parent.getDisplay();
        final Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout());
        infoLabel = new Label(composite, SWT.NONE);
        GridData data = new GridData();
        data.widthHint = 200;
        data.horizontalAlignment = SWT.END;
        infoLabel.setLayoutData(data);
        infoLabel.setToolTipText("Shows information about node / relationship count");
        // schedule worker thread; will be rescheduled 2000ms after last execution end
        executorService.scheduleWithFixedDelay(this, 0, 2000, TimeUnit.MILLISECONDS);
        composite.pack();
        return composite;
    }

    @Override
    public void run() {
        if (Activator.getInstance().updateNeeded()) {

            display.syncExec(new Runnable() {
                @Override
                public void run() {
                    if (!infoLabel.isDisposed()) {
                        CdoManager cdoManager = Activator.getInstance().getCdoManager();
                        String info = "- error -";
                        if (cdoManager != null) {
                            cdoManager.currentTransaction().begin();
                            try {
                                Result<CompositeRowObject> result = cdoManager.createQuery(countQuery).execute();
                                if (result.hasResult()) {
                                    CompositeRowObject counts = result.iterator().next();
                                    long nodes = counts.get("nodes", Long.class).longValue();
                                    long rels = counts.get("rels", Long.class).longValue();
                                    info = String.format("n: %d - r: %d", nodes, rels);
                                } else {
                                    info = "- unknown -";
                                }
                                cdoManager.currentTransaction().commit();
                            } catch (CdoException e) {
                                cdoManager.currentTransaction().rollback();
                                Activator.getInstance().getLog()
                                        .log(new Status(IStatus.ERROR, Activator.ID_PLUGIN, "Error while counting nodes and rels", e));
                            }
                        }
                        infoLabel.setText(info);
                        infoLabel.update();
                    }
                }
            });

        }
    }

}
